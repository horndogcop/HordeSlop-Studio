package net.hordeslop.studio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class ImageMetadataExtractorTest {

    @Test
    fun testExtractA1111PngMetadata() {
        val a1111Text = """
            cyberpunk neon cat on street, 8k, photorealistic
            Negative prompt: blurry, ugly, low quality, deformed
            Steps: 28, Sampler: Euler a, Schedule type: Karras, CFG scale: 7.5, Seed: 987654321, Size: 768x1024, Model: dreamshaper_8, Clip skip: 2
        """.trimIndent()

        val pngBytes = buildFakePngWithTextChunk("parameters", a1111Text)
        val meta = ImageMetadataExtractor.extractFromBytes(pngBytes)

        assertEquals("PNG", meta.format)
        assertEquals("Automatic1111 / WebUI", meta.generatorSource)
        assertEquals("cyberpunk neon cat on street, 8k, photorealistic", meta.prompt)
        assertEquals("blurry, ugly, low quality, deformed", meta.negativePrompt)
        assertEquals("dreamshaper_8", meta.model)
        assertEquals(28, meta.steps)
        assertEquals(7.5f, meta.cfg ?: 0f, 0.01f)
        assertEquals("Euler a", meta.sampler)
        assertEquals("Karras", meta.scheduler)
        assertEquals("987654321", meta.seed)
        assertEquals(768, meta.width)
        assertEquals(1024, meta.height)
        assertEquals(2, meta.clipSkip)
        assertTrue(meta.hasMeaningfulMetadata)
    }

    @Test
    fun testExtractComfyUiJsonMetadata() {
        val comfyJson = """
            {
              "3": {
                "class_type": "KSampler",
                "inputs": {
                  "cfg": 8.0,
                  "denoise": 1.0,
                  "latent_image": ["5", 0],
                  "model": ["4", 0],
                  "negative": ["7", 0],
                  "positive": ["6", 0],
                  "sampler_name": "dpmpp_2m",
                  "scheduler": "karras",
                  "seed": "42424242",
                  "steps": 25
                }
              },
              "4": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": {
                  "ckpt_name": "sd_xl_base_1.0.safetensors"
                }
              },
              "5": {
                "class_type": "EmptyLatentImage",
                "inputs": {
                  "batch_size": 1,
                  "height": 1024,
                  "width": 1024
                }
              },
              "6": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                  "text": "majestic dragon soaring over snowy mountain peak"
                }
              },
              "7": {
                "class_type": "CLIPTextEncode",
                "inputs": {
                  "text": "watermark, text, blurry"
                }
              }
            }
        """.trimIndent()

        val pngBytes = buildFakePngWithTextChunk("prompt", comfyJson)
        val meta = ImageMetadataExtractor.extractFromBytes(pngBytes)

        assertEquals("PNG", meta.format)
        assertEquals("ComfyUI", meta.generatorSource)
        assertEquals("majestic dragon soaring over snowy mountain peak", meta.prompt)
        assertEquals("watermark, text, blurry", meta.negativePrompt)
        assertEquals("sd_xl_base_1.0.safetensors", meta.model)
        assertEquals(25, meta.steps)
        assertEquals(8.0f, meta.cfg ?: 0f, 0.01f)
        assertEquals("dpmpp_2m", meta.sampler)
        assertEquals("karras", meta.scheduler)
        assertEquals("42424242", meta.seed)
        assertEquals(1024, meta.width)
        assertEquals(1024, meta.height)
    }

    @Test
    fun testExtractHordeSlopJsonMetadata() {
        val hordeJson = """
            {
              "generator": "HordeSlop Studio",
              "prompt": "futuristic flying car ### cartoon, lowres",
              "models": ["ICBINP - I Can't Believe It's Not Photography"],
              "params": {
                "steps": 30,
                "cfg_scale": 6.5,
                "sampler_name": "k_dpmpp_2m",
                "scheduler": "karras",
                "seed": "11223344",
                "width": 512,
                "height": 768,
                "clip_skip": 1
              }
            }
        """.trimIndent()

        val rawFields = mapOf("UserComment" to hordeJson)
        val meta = ImageMetadataExtractor.interpretFields("WebP", rawFields)

        assertEquals("WebP", meta.format)
        assertEquals("HordeSlop Studio", meta.generatorSource)
        assertEquals("futuristic flying car", meta.prompt)
        assertEquals("cartoon, lowres", meta.negativePrompt)
        assertEquals("ICBINP - I Can't Believe It's Not Photography", meta.model)
        assertEquals(30, meta.steps)
        assertEquals(6.5f, meta.cfg ?: 0f, 0.01f)
        assertEquals("k_dpmpp_2m", meta.sampler)
        assertEquals("karras", meta.scheduler)
        assertEquals("11223344", meta.seed)
        assertEquals(512, meta.width)
        assertEquals(768, meta.height)
    }

    @Test
    fun testExtractLoraInPrompt() {
        val a1111WithLora = """
            1girl, solo, armor <lora:mech_suit_v2:0.8> <lora:glow_eyes>
            Negative prompt: bad hands
            Steps: 20, Sampler: Euler, CFG scale: 7, Seed: 100
        """.trimIndent()

        val parsed = ImageMetadataExtractor.interpretFields("PNG", mapOf("parameters" to a1111WithLora))
        assertEquals(2, parsed.loras.size)
        assertTrue(parsed.loras.any { it.contains("mech_suit_v2") })
        assertTrue(parsed.loras.any { it.contains("glow_eyes") })
    }

    private fun buildFakePngWithTextChunk(key: String, text: String): ByteArray {
        val bos = ByteArrayOutputStream()
        // PNG signature
        bos.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        // IHDR chunk (13 bytes)
        val ihdrData = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN)
            .putInt(512) // width
            .putInt(512) // height
            .put(8) // bit depth
            .put(6) // color type
            .put(0) // compression
            .put(0) // filter
            .put(0) // interlace
            .array()
        writeChunk(bos, "IHDR", ihdrData)

        // tEXt chunk
        val keyBytes = key.toByteArray(Charsets.ISO_8859_1)
        val valBytes = text.toByteArray(Charsets.UTF_8)
        val chunkData = ByteArray(keyBytes.size + 1 + valBytes.size)
        System.arraycopy(keyBytes, 0, chunkData, 0, keyBytes.size)
        chunkData[keyBytes.size] = 0 // null separator
        System.arraycopy(valBytes, 0, chunkData, keyBytes.size + 1, valBytes.size)
        writeChunk(bos, "tEXt", chunkData)

        // IEND chunk
        writeChunk(bos, "IEND", ByteArray(0))
        return bos.toByteArray()
    }

    private fun writeChunk(bos: ByteArrayOutputStream, type: String, data: ByteArray) {
        val length = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array()
        bos.write(length)

        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        bos.write(typeBytes)
        bos.write(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc.value.toInt()).array()
        bos.write(crcBytes)
    }
}
