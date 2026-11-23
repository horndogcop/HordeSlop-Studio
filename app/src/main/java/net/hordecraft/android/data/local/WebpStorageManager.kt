package net.hordecraft.android.data.local

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class StoredWebpResult(
    val uriString: String,
    val destinationLabel: String,
)

class WebpStorageManager(private val context: Context) {

    suspend fun saveGeneratedWebp(
        rawBytes: ByteArray,
        generationId: String,
        requestId: String,
        prompt: String,
        negativePrompt: String,
        model: String,
        seed: String,
        steps: Int,
        cfg: Float,
        clipSkip: Int,
        sampler: String,
        scheduler: String,
        width: Int,
        height: Int,
        settings: AppSettings,
    ): StoredWebpResult = withContext(Dispatchers.IO) {
        val tempWebp = File.createTempFile("hordecraft_$generationId", ".webp", context.cacheDir)
        try {
            val webpBytes = ensureWebpBytes(rawBytes)
            tempWebp.writeBytes(webpBytes)
            embedExifMetadata(
                file = tempWebp,
                generationId = generationId,
                requestId = requestId,
                prompt = prompt,
                negativePrompt = negativePrompt,
                model = model,
                seed = seed,
                steps = steps,
                cfg = cfg,
                clipSkip = clipSkip,
                sampler = sampler,
                scheduler = scheduler,
                width = width,
                height = height,
            )

            val fileName = "hordecraft_${generationId.take(12)}_${System.currentTimeMillis()}.webp"
            val treeUri = settings.safTreeUri?.let(Uri::parse)

            if (settings.storageMode.name == "SAF" && treeUri != null) {
                val parent = DocumentFile.fromTreeUri(context, treeUri)
                val targetDoc = parent?.createFile("image/webp", fileName)
                if (targetDoc != null) {
                    context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                        tempWebp.inputStream().copyTo(out)
                    }
                    return@withContext StoredWebpResult(
                        uriString = targetDoc.uri.toString(),
                        destinationLabel = settings.safFolderLabel,
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "image/webp")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Hordecraft")
                }
                val itemUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (itemUri != null) {
                    context.contentResolver.openOutputStream(itemUri)?.use { out ->
                        tempWebp.inputStream().copyTo(out)
                    }
                    return@withContext StoredWebpResult(
                        uriString = itemUri.toString(),
                        destinationLabel = "Downloads/Hordecraft",
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Hordecraft",
                )
                downloadsDir.mkdirs()
                if (downloadsDir.canWrite()) {
                    val outFile = File(downloadsDir, fileName)
                    tempWebp.copyTo(outFile, overwrite = true)
                    return@withContext StoredWebpResult(
                        uriString = Uri.fromFile(outFile).toString(),
                        destinationLabel = "Downloads/Hordecraft",
                    )
                }
            }

            // Internal app-private fallback if external storage unavailable
            val internalDir = File(context.filesDir, "images").apply { mkdirs() }
            val internalFile = File(internalDir, fileName)
            tempWebp.copyTo(internalFile, overwrite = true)
            StoredWebpResult(
                uriString = Uri.fromFile(internalFile).toString(),
                destinationLabel = "App Storage/images",
            )
        } finally {
            tempWebp.delete()
        }
    }

    private fun ensureWebpBytes(rawBytes: ByteArray): ByteArray {
        val isWebpHeader = rawBytes.size >= 12 &&
            rawBytes[0] == 'R'.code.toByte() &&
            rawBytes[1] == 'I'.code.toByte() &&
            rawBytes[2] == 'F'.code.toByte() &&
            rawBytes[3] == 'F'.code.toByte() &&
            rawBytes[8] == 'W'.code.toByte() &&
            rawBytes[9] == 'E'.code.toByte() &&
            rawBytes[10] == 'B'.code.toByte() &&
            rawBytes[11] == 'P'.code.toByte()
        if (isWebpHeader) return rawBytes

        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: return rawBytes
        return try {
            val out = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                Bitmap.CompressFormat.WEBP
            }
            decoded.compress(format, 100, out)
            out.toByteArray()
        } finally {
            decoded.recycle()
        }
    }

    private fun embedExifMetadata(
        file: File,
        generationId: String,
        requestId: String,
        prompt: String,
        negativePrompt: String,
        model: String,
        seed: String,
        steps: Int,
        cfg: Float,
        clipSkip: Int,
        sampler: String,
        scheduler: String,
        width: Int,
        height: Int,
    ) {
        try {
            val commentJson: JsonObject = buildJsonObject {
                put("request_id", requestId)
                put("generation_id", generationId)
                put("prompt", prompt)
                put("negative_prompt", negativePrompt)
                put("model", model)
                put("seed", seed)
                put("steps", steps)
                put("cfg_scale", cfg)
                put("clip_skip", clipSkip)
                put("sampler", sampler)
                put("scheduler", scheduler)
                put("width", width)
                put("height", height)
            }
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Hordecraft")
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, prompt)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, commentJson.toString())
            exif.saveAttributes()
        } catch (_: Throwable) {
            // Non-fatal if ExifInterface cannot rewrite chunks on older WebP variants
        }
    }

    suspend fun deleteBackedFile(uriString: String): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return@withContext false
        when (uri.scheme) {
            "file" -> uri.path?.let { File(it).delete() } ?: false
            "content" -> runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            else -> false
        }
    }

    fun copyText(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    suspend fun copyImageToClipboard(uriString: String) = withContext(Dispatchers.IO) {
        val contentUri = toShareableContentUri(uriString) ?: return@withContext
        withContext(Dispatchers.Main) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(context.contentResolver, "Hordecraft Image", contentUri))
        }
    }

    suspend fun createShareIntent(uriString: String): Intent? = withContext(Dispatchers.IO) {
        val contentUri = toShareableContentUri(uriString) ?: return@withContext null
        Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun createOpenInIntent(uriString: String): Intent? = withContext(Dispatchers.IO) {
        val contentUri = toShareableContentUri(uriString) ?: return@withContext null
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "image/webp")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun toShareableContentUri(uriString: String): Uri? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (uri.scheme == "content") return uri
        val sourceFile = uri.path?.let(::File)?.takeIf { it.exists() } ?: return null
        val shareDir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val shareFile = File(shareDir, sourceFile.name)
        FileOutputStream(shareFile).use { out ->
            sourceFile.inputStream().copyTo(out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
    }
}
