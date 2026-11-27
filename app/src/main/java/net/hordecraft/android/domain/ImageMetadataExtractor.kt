package net.hordeslop.studio.domain

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

data class ParsedImageMetadata(
    val format: String, // "PNG", "WebP", "JPEG", "Unknown"
    val generatorSource: String, // e.g. "Automatic1111", "ComfyUI", "AI Horde", "HordeSlop Studio", "Exif"
    val rawFields: Map<String, String> = emptyMap(),
    val prompt: String = "",
    val negativePrompt: String = "",
    val model: String? = null,
    val steps: Int? = null,
    val cfg: Float? = null,
    val sampler: String? = null,
    val scheduler: String? = null,
    val seed: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val clipSkip: Int? = null,
    val rawMetadataText: String = "",
    val loras: List<String> = emptyList(),
) {
    val hasMeaningfulMetadata: Boolean
        get() = prompt.isNotBlank() || !model.isNullOrBlank() || steps != null || seed != null || rawFields.isNotEmpty()
}

object ImageMetadataExtractor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    suspend fun extractFromUri(context: Context, uri: Uri): ParsedImageMetadata {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ParsedImageMetadata(format = "Unknown", generatorSource = "None", rawMetadataText = "Unable to read file")
        return extractFromBytes(bytes)
    }

    fun extractFromBytes(bytes: ByteArray): ParsedImageMetadata {
        if (bytes.size < 12) {
            return ParsedImageMetadata(format = "Unknown", generatorSource = "None")
        }

        return when {
            isPng(bytes) -> extractPng(bytes)
            isWebp(bytes) -> extractWebp(bytes)
            isJpeg(bytes) -> extractJpeg(bytes)
            else -> {
                // Try fallback Exif on arbitrary stream
                val exifMeta = extractExifFallback(bytes)
                exifMeta ?: ParsedImageMetadata(format = "Unknown", generatorSource = "None")
            }
        }
    }

    private fun isPng(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
    }

    private fun isJpeg(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
    }

    private fun isWebp(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        val riff = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
        val webp = bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        return riff && webp
    }

    // ==========================================
    // PNG Parsing (tEXt, iTXt, zTXt chunks)
    // ==========================================
    private fun extractPng(bytes: ByteArray): ParsedImageMetadata {
        val rawFields = mutableMapOf<String, String>()
        var imageWidth: Int? = null
        var imageHeight: Int? = null

        var offset = 8 // skip PNG signature
        while (offset + 8 <= bytes.size) {
            val length = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            val chunkType = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataOffset = offset + 8
            val dataEnd = dataOffset + length

            if (dataEnd + 4 > bytes.size || length < 0) break

            when (chunkType) {
                "IHDR" -> {
                    if (length >= 8) {
                        imageWidth = ByteBuffer.wrap(bytes, dataOffset, 4).order(ByteOrder.BIG_ENDIAN).int
                        imageHeight = ByteBuffer.wrap(bytes, dataOffset + 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    }
                }
                "tEXt" -> {
                    parsePngTextChunk(bytes, dataOffset, length, rawFields)
                }
                "zTXt" -> {
                    parsePngZtxtChunk(bytes, dataOffset, length, rawFields)
                }
                "iTXt" -> {
                    parsePngItxtChunk(bytes, dataOffset, length, rawFields)
                }
                "IEND" -> break
            }

            offset = dataEnd + 4 // skip CRC
        }

        return interpretFields(
            format = "PNG",
            rawFields = rawFields,
            fallbackWidth = imageWidth,
            fallbackHeight = imageHeight,
        )
    }

    private fun parsePngTextChunk(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        out: MutableMap<String, String>,
    ) {
        var nullIndex = -1
        for (i in offset until (offset + length)) {
            if (bytes[i] == 0.toByte()) {
                nullIndex = i
                break
            }
        }
        if (nullIndex in offset until (offset + length)) {
            val key = String(bytes, offset, nullIndex - offset, Charsets.ISO_8859_1)
            val value = String(bytes, nullIndex + 1, (offset + length) - (nullIndex + 1), Charsets.UTF_8)
            out[key] = value
        }
    }

    private fun parsePngZtxtChunk(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        out: MutableMap<String, String>,
    ) {
        var nullIndex = -1
        for (i in offset until (offset + length)) {
            if (bytes[i] == 0.toByte()) {
                nullIndex = i
                break
            }
        }
        if (nullIndex in offset until (offset + length) - 1) {
            val key = String(bytes, offset, nullIndex - offset, Charsets.ISO_8859_1)
            // byte after null is compression method (usually 0 = zlib/deflate)
            val compressedStart = nullIndex + 2
            val compressedLength = (offset + length) - compressedStart
            if (compressedLength > 0) {
                val decompressed = decompressZlib(bytes, compressedStart, compressedLength)
                if (decompressed != null) {
                    out[key] = decompressed
                }
            }
        }
    }

    private fun parsePngItxtChunk(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        out: MutableMap<String, String>,
    ) {
        var null1 = -1
        for (i in offset until (offset + length)) {
            if (bytes[i] == 0.toByte()) {
                null1 = i
                break
            }
        }
        if (null1 !in offset until (offset + length) - 2) return

        val key = String(bytes, offset, null1 - offset, Charsets.UTF_8)
        val compressionFlag = bytes[null1 + 1].toInt()
        // null1 + 2 is compression method
        // null-terminated language tag
        var null2 = -1
        for (i in (null1 + 3) until (offset + length)) {
            if (bytes[i] == 0.toByte()) {
                null2 = i
                break
            }
        }
        if (null2 < 0) return

        // null-terminated translated keyword
        var null3 = -1
        for (i in (null2 + 1) until (offset + length)) {
            if (bytes[i] == 0.toByte()) {
                null3 = i
                break
            }
        }
        if (null3 < 0) return

        val textStart = null3 + 1
        val textLength = (offset + length) - textStart
        if (textLength >= 0) {
            if (compressionFlag == 1) {
                val decompressed = decompressZlib(bytes, textStart, textLength)
                if (decompressed != null) out[key] = decompressed
            } else {
                out[key] = String(bytes, textStart, textLength, Charsets.UTF_8)
            }
        }
    }

    private fun decompressZlib(bytes: ByteArray, offset: Int, length: Int): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(bytes, offset, length)
            val bos = ByteArrayOutputStream(length * 2)
            val buf = ByteArray(1024)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                bos.write(buf, 0, count)
            }
            inflater.end()
            bos.toString("UTF-8")
        }.getOrNull()
    }

    // ==========================================
    // WebP Parsing (RIFF subchunks & EXIF)
    // ==========================================
    private fun extractWebp(bytes: ByteArray): ParsedImageMetadata {
        val rawFields = mutableMapOf<String, String>()
        var imageWidth: Int? = null
        var imageHeight: Int? = null

        // Scan RIFF chunks
        var offset = 12 // after "RIFF....WEBP"
        while (offset + 8 <= bytes.size) {
            val tag = String(bytes, offset, 4, Charsets.US_ASCII)
            val length = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dataOffset = offset + 8
            val dataEnd = dataOffset + length

            if (dataEnd > bytes.size || length < 0) break

            when (tag) {
                "VP8 " -> {
                    if (length >= 10) {
                        // VP8 keyframe width/height at bytes 6..9
                        val w = (bytes[dataOffset + 6].toInt() and 0xFF) or ((bytes[dataOffset + 7].toInt() and 0x3F) shl 8)
                        val h = (bytes[dataOffset + 8].toInt() and 0xFF) or ((bytes[dataOffset + 9].toInt() and 0x3F) shl 8)
                        imageWidth = w
                        imageHeight = h
                    }
                }
                "VP8X" -> {
                    if (length >= 10) {
                        val w = 1 + (bytes[dataOffset + 4].toInt() and 0xFF) or
                            ((bytes[dataOffset + 5].toInt() and 0xFF) shl 8) or
                            ((bytes[dataOffset + 6].toInt() and 0xFF) shl 16)
                        val h = 1 + (bytes[dataOffset + 7].toInt() and 0xFF) or
                            ((bytes[dataOffset + 8].toInt() and 0xFF) shl 8) or
                            ((bytes[dataOffset + 9].toInt() and 0xFF) shl 16)
                        imageWidth = w
                        imageHeight = h
                    }
                }
                "EXIF" -> {
                    parseExifChunk(bytes, dataOffset, length, rawFields)
                }
                "XMP " -> {
                    val xmp = String(bytes, dataOffset, length, Charsets.UTF_8)
                    rawFields["XMP"] = xmp
                }
            }

            offset = dataEnd + (length % 2) // chunks are padded to 2-byte boundary
        }

        // Also run ExifInterface fallback across whole byte stream for WebP
        runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            populateFromExifInterface(exif, rawFields)
        }

        return interpretFields(
            format = "WebP",
            rawFields = rawFields,
            fallbackWidth = imageWidth,
            fallbackHeight = imageHeight,
        )
    }

    // ==========================================
    // JPEG Parsing (Exif & COM marker)
    // ==========================================
    private fun extractJpeg(bytes: ByteArray): ParsedImageMetadata {
        val rawFields = mutableMapOf<String, String>()
        var imageWidth: Int? = null
        var imageHeight: Int? = null

        // 1. Scan for JPEG COM (0xFF 0xFE) marker and XMP APP1
        var offset = 2
        while (offset + 4 <= bytes.size) {
            if (bytes[offset] == 0xFF.toByte()) {
                val marker = bytes[offset + 1].toInt() and 0xFF
                if (marker == 0xDA || marker == 0xD9) break // SOS or EOI, start of scan data

                val len = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                val payloadOffset = offset + 4
                val payloadLen = len - 2

                if (payloadOffset + payloadLen <= bytes.size && payloadLen > 0) {
                    if (marker == 0xFE) { // COM (Comment)
                        val comText = String(bytes, payloadOffset, payloadLen, Charsets.UTF_8)
                        rawFields["Comment"] = comText
                    } else if (marker in 0xC0..0xC3) { // SOF0 - SOF3 (dimensions)
                        if (payloadLen >= 5) {
                            imageHeight = ((bytes[payloadOffset + 1].toInt() and 0xFF) shl 8) or (bytes[payloadOffset + 2].toInt() and 0xFF)
                            imageWidth = ((bytes[payloadOffset + 3].toInt() and 0xFF) shl 8) or (bytes[payloadOffset + 4].toInt() and 0xFF)
                        }
                    }
                }
                offset += 2 + len
            } else {
                offset++
            }
        }

        // 2. ExifInterface
        runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            populateFromExifInterface(exif, rawFields)
            if (imageWidth == null) {
                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                if (w > 0) imageWidth = w
            }
            if (imageHeight == null) {
                val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (h > 0) imageHeight = h
            }
        }

        return interpretFields(
            format = "JPEG",
            rawFields = rawFields,
            fallbackWidth = imageWidth,
            fallbackHeight = imageHeight,
        )
    }

    private fun extractExifFallback(bytes: ByteArray): ParsedImageMetadata? {
        val rawFields = mutableMapOf<String, String>()
        runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            populateFromExifInterface(exif, rawFields)
        }
        if (rawFields.isEmpty()) return null
        return interpretFields(format = "Image", rawFields = rawFields)
    }

    private fun parseExifChunk(bytes: ByteArray, offset: Int, length: Int, out: MutableMap<String, String>) {
        runCatching {
            // Some WebP EXIF chunks have 6-byte "Exif\0\0" header, some don't
            var actualOffset = offset
            var actualLength = length
            if (length >= 6 &&
                bytes[offset] == 'E'.code.toByte() &&
                bytes[offset + 1] == 'x'.code.toByte() &&
                bytes[offset + 2] == 'i'.code.toByte() &&
                bytes[offset + 3] == 'f'.code.toByte()
            ) {
                actualOffset += 6
                actualLength -= 6
            }
            val bais = ByteArrayInputStream(bytes, actualOffset, actualLength)
            val exif = ExifInterface(bais)
            populateFromExifInterface(exif, out)
        }
    }

    private fun populateFromExifInterface(exif: ExifInterface, out: MutableMap<String, String>) {
        val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
        if (!userComment.isNullOrBlank()) out["UserComment"] = cleanExifComment(userComment)

        val imageDesc = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
        if (!imageDesc.isNullOrBlank()) out["Description"] = imageDesc

        val software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)
        if (!software.isNullOrBlank()) out["Software"] = software

        val makerNote = exif.getAttribute(ExifInterface.TAG_MAKER_NOTE)
        if (!makerNote.isNullOrBlank()) out["MakerNote"] = makerNote

        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME)
        if (!dateTime.isNullOrBlank()) out["DateTime"] = dateTime
    }

    private fun cleanExifComment(comment: String): String {
        // ExifInterface sometimes prefixes "ASCII\0\0\0" or "UNICODE\0"
        return comment
            .removePrefix("ASCII\u0000\u0000\u0000")
            .removePrefix("UNICODE\u0000")
            .removePrefix("charset=UTF-8\u0000")
            .trim()
    }

    // ==========================================
    // Parameter Interpretation Engine
    // ==========================================
    fun interpretFields(
        format: String,
        rawFields: Map<String, String>,
        fallbackWidth: Int? = null,
        fallbackHeight: Int? = null,
    ): ParsedImageMetadata {
        val allRawText = buildString {
            rawFields.forEach { (k, v) ->
                appendLine("[$k]")
                appendLine(v)
                appendLine()
            }
        }.trim()

        // 1. Check for Automatic1111 / WebUI / Forge `parameters`
        val a1111Candidate = rawFields["parameters"]
            ?: rawFields["UserComment"]?.takeIf { it.contains("Steps: ") }
            ?: rawFields["Comment"]?.takeIf { it.contains("Steps: ") }
            ?: rawFields["Description"]?.takeIf { it.contains("Steps: ") }

        if (a1111Candidate != null && (a1111Candidate.contains("Steps: ") || a1111Candidate.contains("Negative prompt:"))) {
            return parseA1111Text(format, rawFields, a1111Candidate, allRawText, fallbackWidth, fallbackHeight)
        }

        // 2. Check for ComfyUI JSON graph in `prompt` or `workflow`
        val comfyCandidate = rawFields["prompt"] ?: rawFields["workflow"]
        if (comfyCandidate != null && comfyCandidate.trim().startsWith("{")) {
            val comfyParsed = runCatching { parseComfyUiJson(format, rawFields, comfyCandidate, allRawText, fallbackWidth, fallbackHeight) }.getOrNull()
            if (comfyParsed != null && comfyParsed.hasMeaningfulMetadata) {
                return comfyParsed
            }
        }

        // 3. Check for AI Horde / HordeSlop Studio JSON
        val jsonCandidate = rawFields.values.firstOrNull { it.trim().startsWith("{") && it.contains("\"prompt\"") }
        if (jsonCandidate != null) {
            val hordeParsed = runCatching { parseHordeJson(format, rawFields, jsonCandidate, allRawText, fallbackWidth, fallbackHeight) }.getOrNull()
            if (hordeParsed != null && hordeParsed.hasMeaningfulMetadata) {
                return hordeParsed
            }
        }

        // 4. NovelAI / Fooocus / generic metadata
        val novelAiCandidate = rawFields["Comment"] ?: rawFields["Description"] ?: rawFields["UserComment"]
        if (novelAiCandidate != null && novelAiCandidate.trim().startsWith("{") && novelAiCandidate.contains("\"uc\"")) {
            val naiParsed = runCatching { parseNovelAiJson(format, rawFields, novelAiCandidate, allRawText, fallbackWidth, fallbackHeight) }.getOrNull()
            if (naiParsed != null && naiParsed.hasMeaningfulMetadata) {
                return naiParsed
            }
        }

        // 5. Fallback: single prompt field in Description / UserComment / Comment
        val fallbackPrompt = rawFields["parameters"]
            ?: rawFields["Description"]
            ?: rawFields["UserComment"]
            ?: rawFields["Comment"]
            ?: ""

        val generatorSource = rawFields["Software"]
            ?: if (format == "WebP") "WebP Exif" else "$format Metadata"

        return ParsedImageMetadata(
            format = format,
            generatorSource = generatorSource,
            rawFields = rawFields,
            prompt = fallbackPrompt.trim(),
            negativePrompt = "",
            model = null,
            steps = null,
            cfg = null,
            sampler = null,
            scheduler = null,
            seed = null,
            width = fallbackWidth,
            height = fallbackHeight,
            clipSkip = null,
            rawMetadataText = allRawText,
        )
    }

    private fun parseA1111Text(
        format: String,
        rawFields: Map<String, String>,
        text: String,
        allRawText: String,
        fallbackWidth: Int?,
        fallbackHeight: Int?,
    ): ParsedImageMetadata {
        val lines = text.trim().lines()
        var positivePrompt = ""
        var negativePrompt = ""
        var paramLine = ""

        val negIdx = text.indexOf("Negative prompt:")
        val stepsIdx = text.indexOf("Steps:")

        if (negIdx >= 0) {
            positivePrompt = text.substring(0, negIdx).trim()
            if (stepsIdx > negIdx) {
                negativePrompt = text.substring(negIdx + "Negative prompt:".length, stepsIdx).trim()
                paramLine = text.substring(stepsIdx).trim()
            } else {
                negativePrompt = text.substring(negIdx + "Negative prompt:".length).trim()
            }
        } else if (stepsIdx >= 0) {
            positivePrompt = text.substring(0, stepsIdx).trim()
            paramLine = text.substring(stepsIdx).trim()
        } else {
            positivePrompt = text.trim()
        }

        val paramMap = mutableMapOf<String, String>()
        if (paramLine.isNotBlank()) {
            val parts = paramLine.split(", ")
            for (p in parts) {
                val colon = p.indexOf(':')
                if (colon > 0) {
                    val k = p.substring(0, colon).trim()
                    val v = p.substring(colon + 1).trim()
                    paramMap[k] = v
                }
            }
        }

        val steps = paramMap["Steps"]?.toIntOrNull()
        val sampler = paramMap["Sampler"]
        val scheduler = paramMap["Schedule type"] ?: paramMap["Scheduler"]
        val cfg = paramMap["CFG scale"]?.toFloatOrNull()
        val seed = paramMap["Seed"]
        val model = paramMap["Model"] ?: paramMap["Model hash"]
        val clipSkip = paramMap["Clip skip"]?.toIntOrNull()

        var width = fallbackWidth
        var height = fallbackHeight
        val sizeStr = paramMap["Size"]
        if (sizeStr != null && sizeStr.contains('x')) {
            val sw = sizeStr.substringBefore('x').trim().toIntOrNull()
            val sh = sizeStr.substringAfter('x').trim().toIntOrNull()
            if (sw != null && sh != null) {
                width = sw
                height = sh
            }
        }

        // Extract <lora:name:weight>
        val loraRegex = Regex("<lora:([^:>]+)(?::([^>]+))?>")
        val loras = loraRegex.findAll(positivePrompt).map { m ->
            val name = m.groupValues[1]
            val weight = m.groupValues.getOrNull(2)?.ifBlank { null } ?: "1.0"
            "$name ($weight)"
        }.toList()

        return ParsedImageMetadata(
            format = format,
            generatorSource = "Automatic1111 / WebUI",
            rawFields = rawFields,
            prompt = positivePrompt,
            negativePrompt = negativePrompt,
            model = model,
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            scheduler = scheduler,
            seed = seed,
            width = width,
            height = height,
            clipSkip = clipSkip,
            rawMetadataText = allRawText,
            loras = loras,
        )
    }

    private fun parseComfyUiJson(
        format: String,
        rawFields: Map<String, String>,
        jsonStr: String,
        allRawText: String,
        fallbackWidth: Int?,
        fallbackHeight: Int?,
    ): ParsedImageMetadata {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        var positivePrompt = ""
        var negativePrompt = ""
        var model: String? = null
        var steps: Int? = null
        var cfg: Float? = null
        var sampler: String? = null
        var scheduler: String? = null
        var seed: String? = null
        var width: Int? = fallbackWidth
        var height: Int? = fallbackHeight

        for ((_, nodeElem) in root) {
            val nodeObj = nodeElem as? JsonObject ?: continue
            val classType = nodeObj["class_type"]?.jsonPrimitive?.contentOrNull ?: ""
            val inputs = nodeObj["inputs"] as? JsonObject ?: continue

            when (classType) {
                "KSampler", "KSamplerAdvanced" -> {
                    steps = inputs["steps"]?.jsonPrimitive?.intOrNull
                    cfg = inputs["cfg"]?.jsonPrimitive?.floatOrNull
                    sampler = inputs["sampler_name"]?.jsonPrimitive?.contentOrNull
                    scheduler = inputs["scheduler"]?.jsonPrimitive?.contentOrNull
                    seed = inputs["seed"]?.jsonPrimitive?.contentOrNull
                        ?: inputs["noise_seed"]?.jsonPrimitive?.contentOrNull

                    // Trace positive link
                    val posLink = inputs["positive"] as? JsonArray
                    val posId = posLink?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    if (posId != null) {
                        val posNode = root[posId]?.jsonObject?.get("inputs")?.jsonObject
                        val text = posNode?.get("text")?.jsonPrimitive?.contentOrNull
                        if (!text.isNullOrBlank()) positivePrompt = text
                    }

                    // Trace negative link
                    val negLink = inputs["negative"] as? JsonArray
                    val negId = negLink?.firstOrNull()?.jsonPrimitive?.contentOrNull
                    if (negId != null) {
                        val negNode = root[negId]?.jsonObject?.get("inputs")?.jsonObject
                        val text = negNode?.get("text")?.jsonPrimitive?.contentOrNull
                        if (!text.isNullOrBlank()) negativePrompt = text
                    }
                }
                "CheckpointLoaderSimple" -> {
                    model = inputs["ckpt_name"]?.jsonPrimitive?.contentOrNull
                }
                "EmptyLatentImage" -> {
                    width = inputs["width"]?.jsonPrimitive?.intOrNull ?: width
                    height = inputs["height"]?.jsonPrimitive?.intOrNull ?: height
                }
            }
        }

        return ParsedImageMetadata(
            format = format,
            generatorSource = "ComfyUI",
            rawFields = rawFields,
            prompt = positivePrompt,
            negativePrompt = negativePrompt,
            model = model,
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            scheduler = scheduler,
            seed = seed,
            width = width,
            height = height,
            rawMetadataText = allRawText,
        )
    }

    private fun parseHordeJson(
        format: String,
        rawFields: Map<String, String>,
        jsonStr: String,
        allRawText: String,
        fallbackWidth: Int?,
        fallbackHeight: Int?,
    ): ParsedImageMetadata {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val rawPrompt = root["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        val positivePrompt = rawPrompt.substringBefore("###").trim()
        val negativePrompt = if (rawPrompt.contains("###")) rawPrompt.substringAfter("###").trim() else ""

        val models = root["models"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        val model = models?.firstOrNull()

        val params = root["params"]?.jsonObject
        val steps = params?.get("steps")?.jsonPrimitive?.intOrNull
        val cfg = params?.get("cfg_scale")?.jsonPrimitive?.floatOrNull
        val sampler = params?.get("sampler_name")?.jsonPrimitive?.contentOrNull
        val scheduler = params?.get("scheduler")?.jsonPrimitive?.contentOrNull
        val seed = params?.get("seed")?.jsonPrimitive?.contentOrNull
        val clipSkip = params?.get("clip_skip")?.jsonPrimitive?.intOrNull
        val width = params?.get("width")?.jsonPrimitive?.intOrNull ?: fallbackWidth
        val height = params?.get("height")?.jsonPrimitive?.intOrNull ?: fallbackHeight

        val generatorSource = root["generator"]?.jsonPrimitive?.contentOrNull
            ?: if (format == "WebP") "HordeSlop Studio (WebP Exif)" else "AI Horde"

        return ParsedImageMetadata(
            format = format,
            generatorSource = generatorSource,
            rawFields = rawFields,
            prompt = positivePrompt,
            negativePrompt = negativePrompt,
            model = model,
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            scheduler = scheduler,
            seed = seed,
            width = width,
            height = height,
            clipSkip = clipSkip,
            rawMetadataText = allRawText,
        )
    }

    private fun parseNovelAiJson(
        format: String,
        rawFields: Map<String, String>,
        jsonStr: String,
        allRawText: String,
        fallbackWidth: Int?,
        fallbackHeight: Int?,
    ): ParsedImageMetadata {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val prompt = root["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
        val uc = root["uc"]?.jsonPrimitive?.contentOrNull ?: ""
        val steps = root["steps"]?.jsonPrimitive?.intOrNull
        val cfg = root["scale"]?.jsonPrimitive?.floatOrNull
        val seed = root["seed"]?.jsonPrimitive?.contentOrNull
        val sampler = root["sampler"]?.jsonPrimitive?.contentOrNull
        val width = root["width"]?.jsonPrimitive?.intOrNull ?: fallbackWidth
        val height = root["height"]?.jsonPrimitive?.intOrNull ?: fallbackHeight

        return ParsedImageMetadata(
            format = format,
            generatorSource = "NovelAI",
            rawFields = rawFields,
            prompt = prompt,
            negativePrompt = uc,
            model = "NovelAI Diffusion",
            steps = steps,
            cfg = cfg,
            sampler = sampler,
            seed = seed,
            width = width,
            height = height,
            rawMetadataText = allRawText,
        )
    }
}
