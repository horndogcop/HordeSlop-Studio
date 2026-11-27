package net.hordeslop.studio.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedGenerationUrlResult(
    val hasMetadata: Boolean,
    val metadata: ParsedImageMetadata,
    val originalUrl: String,
)

object GenerationUrlParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val urlExtractRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun parse(uri: Uri): ParsedGenerationUrlResult =
        parseString(uri.toString()) ?: emptyResult(uri.toString())

    fun parseString(text: String): ParsedGenerationUrlResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val candidateUrl = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> urlExtractRegex.find(trimmed)?.value ?: return null
        }

        val sanitizedUrl = sanitizeUrlForUriCreate(candidateUrl)
        val parsedUri = runCatching { URI.create(sanitizedUrl) }.getOrNull() ?: return null
        val scheme = parsedUri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null

        val host = parsedUri.host?.lowercase().orEmpty()
        val path = parsedUri.path?.lowercase().orEmpty()
        val generatorSource = when {
            host.contains("tinybots") || host.contains("artbot") -> "ArtBot"
            host.contains("aihorde") || host.contains("stablehorde") -> "AI Horde"
            host.contains("stableui") || host.contains("stable-ui") ||
                (host.contains("aqualxx") && path.contains("stable-ui")) -> "StableUI"
            else -> "Web Link"
        }

        val params = LinkedHashMap<String, String>()

        // 1. Collect standard query parameters from rawQuery
        collectQueryPairs(parsedUri.rawQuery, params)

        // 2. Collect query-like parameters from rawFragment (e.g. #create?prompt=... or #prompt=...&steps=25)
        val rawFragment = parsedUri.rawFragment
        if (!rawFragment.isNullOrBlank()) {
            parseFragmentParameters(rawFragment, params)
        }

        // 3. Inspect AI Horde studio link parameters (readLink from AiHordeFrontpage studio-link.ts) or nested JSON payloads
        var jsonPrompt = ""
        var jsonNegPrompt = ""
        var jsonModel: String? = null
        var jsonSteps: Int? = null
        var jsonCfg: Float? = null
        var jsonSampler: String? = null
        var jsonScheduler: String? = null
        var jsonSeed: String? = null
        var jsonWidth: Int? = null
        var jsonHeight: Int? = null
        var jsonClipSkip: Int? = null
        val jsonLoras = mutableListOf<String>()

        val studioPayload = readLink { key -> params[key] }
        val rootJson = if (studioPayload != null) {
            studioPayload.styleKey?.let {
                params["styleKey"] = it
                params["style"] = it.substringAfter(':')
            }
            studioPayload.title?.let {
                params["title"] = it
            }
            if (studioPayload.overridesStyleLoras) {
                params["overridesStyleLoras"] = "true"
            }
            studioPayload.request
        } else {
            val jsonCandidate = listOfNotNull(
                params["share"],
                params["request"],
                params["params"],
                params["create"],
                params["data"],
                params["json"],
            ).firstOrNull()
            if (jsonCandidate != null) {
                val decodedJson = decodeJsonOrBase64(jsonCandidate)
                if (decodedJson != null && decodedJson.startsWith("{")) {
                    runCatching { json.parseToJsonElement(decodedJson).jsonObject }.getOrNull()
                } else null
            } else null
        }

        if (rootJson != null) {
            val rawPrompt = rootJson["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (rawPrompt.contains("###")) {
                jsonPrompt = rawPrompt.substringBefore("###").trim()
                jsonNegPrompt = rawPrompt.substringAfter("###").trim()
            } else {
                jsonPrompt = rawPrompt
                jsonNegPrompt = rootJson["negative_prompt"]?.jsonPrimitive?.contentOrNull
                    ?: rootJson["negative"]?.jsonPrimitive?.contentOrNull
                    ?: rootJson["uc"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }

            jsonModel = runCatching {
                rootJson["models"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: rootJson["model"]?.jsonPrimitive?.contentOrNull

            val pObj = runCatching { rootJson["params"]?.jsonObject }.getOrNull() ?: rootJson
            jsonSteps = pObj["steps"]?.jsonPrimitive?.intOrNull
            jsonCfg = pObj["cfg_scale"]?.jsonPrimitive?.floatOrNull ?: pObj["cfg"]?.jsonPrimitive?.floatOrNull
            jsonSampler = pObj["sampler_name"]?.jsonPrimitive?.contentOrNull ?: pObj["sampler"]?.jsonPrimitive?.contentOrNull
            jsonScheduler = pObj["scheduler"]?.jsonPrimitive?.contentOrNull
            if (jsonScheduler == null && pObj["karras"]?.jsonPrimitive?.contentOrNull == "true") {
                jsonScheduler = "karras"
            }
            jsonSeed = pObj["seed"]?.jsonPrimitive?.contentOrNull
            jsonWidth = pObj["width"]?.jsonPrimitive?.intOrNull
            jsonHeight = pObj["height"]?.jsonPrimitive?.intOrNull
            jsonClipSkip = pObj["clip_skip"]?.jsonPrimitive?.intOrNull

            val loraArray = runCatching { pObj["loras"]?.jsonArray ?: rootJson["loras"]?.jsonArray }.getOrNull()
            loraArray?.forEach { elem ->
                runCatching {
                    val obj = elem.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                    val strength = obj["model"]?.jsonPrimitive?.floatOrNull ?: 1.0f
                    if (!name.isNullOrBlank()) jsonLoras += "$name ($strength)"
                }.onFailure {
                    elem.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() }?.let { s ->
                        jsonLoras += s
                    }
                }
            }
        }

        // 4. Extract parameters from direct query/fragment keys
        val rawPrompt = params["prompt"] ?: params["p"] ?: params["prompt_text"] ?: jsonPrompt
        var prompt = rawPrompt.trim()
        var negativePrompt = (
            params["negative_prompt"]
                ?: params["negative"]
                ?: params["np"]
                ?: params["neg_prompt"]
                ?: jsonNegPrompt
            ).trim()

        if (prompt.contains("###")) {
            val parts = prompt.split("###", limit = 2)
            prompt = parts[0].trim()
            if (negativePrompt.isBlank()) {
                negativePrompt = parts[1].trim()
            }
        }

        val rawModel = params["model"] ?: params["models"] ?: params["m"] ?: jsonModel
        val model = normalizeModelParam(rawModel)
        val steps = params["steps"]?.toIntOrNull() ?: params["step"]?.toIntOrNull() ?: jsonSteps
        val cfg = params["cfg_scale"]?.toFloatOrNull()
            ?: params["cfg"]?.toFloatOrNull()
            ?: params["scale"]?.toFloatOrNull()
            ?: jsonCfg
        val sampler = params["sampler"] ?: params["sampler_name"] ?: jsonSampler
        var scheduler = params["scheduler"] ?: params["schedule"] ?: jsonScheduler
        if (scheduler == null && (params["karras"] == "true" || params["karras"] == "1")) {
            scheduler = "karras"
        }

        val seed = params["seed"]?.ifBlank { null } ?: jsonSeed?.ifBlank { null }

        var width = params["width"]?.toIntOrNull() ?: jsonWidth
        var height = params["height"]?.toIntOrNull() ?: jsonHeight
        val sizeParam = params["size"] ?: params["resolution"]
        if (sizeParam != null && sizeParam.contains('x', ignoreCase = true)) {
            val parts = sizeParam.lowercase().split('x', limit = 2)
            val w = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (w != null && h != null) {
                width = w
                height = h
            }
        }

        val clipSkip = params["clip_skip"]?.toIntOrNull() ?: params["clip"]?.toIntOrNull() ?: jsonClipSkip

        val hasMetadata = prompt.isNotBlank() ||
            negativePrompt.isNotBlank() ||
            !model.isNullOrBlank() ||
            steps != null ||
            cfg != null ||
            !sampler.isNullOrBlank() ||
            !scheduler.isNullOrBlank() ||
            seed != null ||
            (width != null && height != null) ||
            clipSkip != null ||
            jsonLoras.isNotEmpty() ||
            studioPayload != null

        val meta = ParsedImageMetadata(
            format = "URL",
            generatorSource = generatorSource,
            rawFields = params,
            prompt = prompt,
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
            rawMetadataText = candidateUrl,
            loras = jsonLoras,
        )

        return ParsedGenerationUrlResult(
            hasMetadata = hasMetadata,
            metadata = meta,
            originalUrl = candidateUrl,
        )
    }

    private fun emptyResult(url: String) = ParsedGenerationUrlResult(
        hasMetadata = false,
        metadata = ParsedImageMetadata(
            format = "URL",
            generatorSource = "Web Link",
            rawMetadataText = url,
        ),
        originalUrl = url,
    )

    private fun collectQueryPairs(rawQuery: String?, out: MutableMap<String, String>) {
        if (rawQuery.isNullOrBlank()) return
        rawQuery.split('&').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val rawKey = pair.substring(0, idx)
                val rawVal = pair.substring(idx + 1)
                val key = percentDecode(rawKey, decodePlusAsSpace = true).lowercase().trim()
                val decodePlus = key !in BASE64_FRIENDLY_KEYS
                val value = percentDecode(rawVal, decodePlusAsSpace = decodePlus).trim()
                if (key.isNotEmpty() && !out.containsKey(key)) {
                    out[key] = value
                }
            }
        }
    }

    private fun normalizeModelParam(raw: String?): String? {
        val trimmed = raw?.trim()?.ifBlank { null } ?: return null
        if (trimmed.startsWith("[")) {
            val fromJson = runCatching {
                json.parseToJsonElement(trimmed).jsonArray.firstOrNull()?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            if (!fromJson.isNullOrBlank()) return fromJson.trim()
        }
        return trimmed.substringBefore(',').trim().ifBlank { null }
    }

    private fun sanitizeUrlForUriCreate(url: String): String {
        val sanitizedHash = url.replace("###", "%23%23%23")
        val firstHashIdx = sanitizedHash.indexOf('#')
        val urlWithSingleHash = if (firstHashIdx != -1) {
            val before = sanitizedHash.substring(0, firstHashIdx + 1)
            val after = sanitizedHash.substring(firstHashIdx + 1).replace("#", "%23")
            before + after
        } else {
            sanitizedHash
        }

        val sb = StringBuilder(urlWithSingleHash.length)
        var i = 0
        while (i < urlWithSingleHash.length) {
            val c = urlWithSingleHash[i]
            if (c == '%') {
                val hasTwoHex = i + 2 < urlWithSingleHash.length &&
                    isHexDigit(urlWithSingleHash[i + 1]) &&
                    isHexDigit(urlWithSingleHash[i + 2])
                if (hasTwoHex) {
                    sb.append('%')
                } else {
                    sb.append("%25")
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun isHexDigit(c: Char): Boolean =
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

    private fun parseFragmentParameters(rawFragment: String, out: MutableMap<String, String>) {
        val candidates = mutableListOf<String>()
        candidates += rawFragment
        val decodedFull = percentDecode(rawFragment, decodePlusAsSpace = false)
        if (decodedFull != rawFragment) {
            candidates += decodedFull
        }

        if (rawFragment.contains('#') || decodedFull.contains('#')) {
            (rawFragment.split('#') + decodedFull.split('#')).forEach { token ->
                val t = token.trim()
                if (t.isNotEmpty()) candidates += t
            }
        }

        for (candidate in candidates) {
            val queryPart = when {
                candidate.contains('?') -> candidate.substringAfter('?')
                candidate.contains('=') -> candidate
                else -> ""
            }
            if (queryPart.isNotEmpty()) {
                collectQueryPairs(queryPart, out)
            }
        }
    }

    private fun decodeJsonOrBase64(candidate: String): String? {
        var trimmed = candidate.trim()
        if (trimmed.isEmpty()) return null

        while (trimmed.startsWith("%7b", ignoreCase = true) || trimmed.startsWith("%257b", ignoreCase = true)) {
            val decoded = percentDecode(trimmed, decodePlusAsSpace = false).trim()
            if (decoded == trimmed) break
            trimmed = decoded
        }

        if (trimmed.startsWith("{")) {
            val isValid = runCatching {
                json.parseToJsonElement(trimmed) is kotlinx.serialization.json.JsonObject
            }.getOrDefault(false)
            return if (isValid) trimmed else null
        }

        val normalized = trimmed.replace('-', '+').replace('_', '/')
        if (normalized.length % 4 == 1) return null

        val padded = when (normalized.length % 4) {
            2 -> "$normalized=="
            3 -> "$normalized="
            else -> normalized
        }
        return runCatching {
            val decodedBytes = decodeBase64Bytes(padded) ?: return null
            var decodedString = String(decodedBytes, Charsets.UTF_8).trim()
            while (decodedString.startsWith("%7b", ignoreCase = true)) {
                val d = percentDecode(decodedString, decodePlusAsSpace = false).trim()
                if (d == decodedString) break
                decodedString = d
            }
            if (decodedString.startsWith("{") &&
                runCatching { json.parseToJsonElement(decodedString) is kotlinx.serialization.json.JsonObject }.getOrDefault(false)
            ) {
                decodedString
            } else {
                null
            }
        }.getOrNull()
    }

    private fun decodeBase64Bytes(input: String): ByteArray? {
        val clean = input.filter { !it.isWhitespace() }
        if (clean.isEmpty() || clean.length % 4 != 0) return null
        val out = ByteArrayOutputStream((clean.length / 4) * 3)
        var i = 0
        while (i < clean.length) {
            val c0 = base64Index(clean[i])
            val c1 = base64Index(clean[i + 1])
            val ch2 = clean[i + 2]
            val ch3 = clean[i + 3]
            if (c0 < 0 || c1 < 0) return null
            val b0 = (c0 shl 2) or (c1 shr 4)
            out.write(b0)
            if (ch2 != '=') {
                val c2 = base64Index(ch2)
                if (c2 < 0) return null
                out.write(((c1 and 0x0F) shl 4) or (c2 shr 2))
                if (ch3 != '=') {
                    val c3 = base64Index(ch3)
                    if (c3 < 0) return null
                    out.write(((c2 and 0x03) shl 6) or c3)
                }
            } else if (ch3 != '=') {
                return null
            }
            i += 4
        }
        return out.toByteArray()
    }

    private fun base64Index(ch: Char): Int = when (ch) {
        in 'A'..'Z' -> ch - 'A'
        in 'a'..'z' -> ch - 'a' + 26
        in '0'..'9' -> ch - '0' + 52
        '+' -> 62
        '/' -> 63
        else -> -1
    }

    private fun percentDecode(input: String, decodePlusAsSpace: Boolean): String {
        val out = ByteArrayOutputStream(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '%' && i + 2 < input.length -> {
                    val hi = Character.digit(input[i + 1], 16)
                    val lo = Character.digit(input[i + 2], 16)
                    if (hi >= 0 && lo >= 0) {
                        out.write((hi shl 4) or lo)
                        i += 3
                    } else {
                        out.write(ch.code)
                        i++
                    }
                }
                ch == '+' && decodePlusAsSpace -> {
                    out.write(' '.code)
                    i++
                }
                else -> {
                    val bytes = ch.toString().toByteArray(Charsets.UTF_8)
                    out.write(bytes)
                    i++
                }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private val BASE64_FRIENDLY_KEYS = setOf("share", "request", "params", "create", "data", "json", "r")

    private const val STUDIO_LINK_VERSION = 1
    const val STUDIO_LINK_MAX_PARAM_CHARS = 4096
    private const val MAX_UNPACKED_BYTES = 32768

    data class StudioLinkPayload(
        val request: JsonObject,
        val styleKey: String?,
        val overridesStyleLoras: Boolean,
        val kind: String, // "simple" | "full"
        val title: String?,
    )

    fun readLink(get: (String) -> String?): StudioLinkPayload? {
        val packed = get("r")
        val unpacked = if (!packed.isNullOrBlank()) unpackStudioRequest(packed.trim()) else null
        val model = get("model")?.trim().orEmpty()
        val style = get("style")?.trim().orEmpty()
        val styleId = (get("styleid") ?: get("styleId"))?.trim().orEmpty()
        val givenTitle = get("title")?.trim().orEmpty()
        val title = if (givenTitle.isNotEmpty() && givenTitle.length <= STUDIO_LINK_MAX_PARAM_CHARS) {
            givenTitle
        } else {
            null
        }

        val named = when {
            styleId.isNotEmpty() -> "native:$styleId"
            style.isNotEmpty() -> "curated:$style"
            else -> null
        }

        if (unpacked != null) {
            val reqMap = unpacked.request.toMutableMap()
            if (model.isNotEmpty()) {
                reqMap["models"] = JsonArray(listOf(JsonPrimitive(model)))
            }
            val finalStyleKey = named ?: unpacked.styleKey
            val overrides = unpacked.overridesStyleLoras &&
                unpacked.styleKey != null &&
                (named == null || named == unpacked.styleKey)
            return StudioLinkPayload(
                request = JsonObject(reqMap),
                styleKey = finalStyleKey,
                overridesStyleLoras = overrides,
                kind = "full",
                title = title,
            )
        }

        if (model.isEmpty() && named == null) {
            return null
        }

        val simpleReq = buildMap {
            put("prompt", JsonPrimitive(""))
            if (model.isNotEmpty()) {
                put("models", JsonArray(listOf(JsonPrimitive(model))))
            }
        }
        return StudioLinkPayload(
            request = JsonObject(simpleReq),
            styleKey = named,
            overridesStyleLoras = false,
            kind = "simple",
            title = title,
        )
    }

    private data class UnpackedStudioRequest(
        val request: JsonObject,
        val styleKey: String?,
        val overridesStyleLoras: Boolean,
    )

    private fun unpackStudioRequest(packed: String): UnpackedStudioRequest? {
        if (packed.isEmpty() || packed.length > STUDIO_LINK_MAX_PARAM_CHARS) return null
        val bytes = fromBase64Url(packed) ?: return null
        val inflated = inflateDeflateBytes(bytes) ?: return null
        if (inflated.size > MAX_UNPACKED_BYTES) return null
        val jsonString = runCatching { String(inflated, Charsets.UTF_8) }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull() ?: return null
        if (root["v"]?.jsonPrimitive?.intOrNull != STUDIO_LINK_VERSION) return null
        val rawRequest = root["request"]?.jsonObject ?: return null
        val styleId = (root["styleId"] ?: root["styleid"])?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val style = root["style"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val styleKey = when {
            styleId.isNotEmpty() -> "native:$styleId"
            style.isNotEmpty() -> "curated:$style"
            else -> null
        }
        val overrides = root["overridesStyleLoras"]?.jsonPrimitive?.booleanOrNull == true
        return UnpackedStudioRequest(
            request = rawRequest,
            styleKey = styleKey,
            overridesStyleLoras = overrides,
        )
    }

    private fun fromBase64Url(text: String): ByteArray? {
        if (text.isEmpty() || text.length > STUDIO_LINK_MAX_PARAM_CHARS) return null
        for (i in 0 until text.length) {
            val c = text[i]
            val valid = (c in 'A'..'Z') || (c in 'a'..'z') || (c in '0'..'9') || c == '-' || c == '_'
            if (!valid) return null
        }
        val padded = when (text.length % 4) {
            2 -> "$text=="
            3 -> "$text="
            1 -> return null
            else -> text
        }.replace('-', '+').replace('_', '/')
        return decodeBase64Bytes(padded)
    }

    private fun inflateDeflateBytes(bytes: ByteArray, maxBytes: Int = MAX_UNPACKED_BYTES): ByteArray? {
        val rawInflater = java.util.zip.Inflater(true)
        val rawResult = runCatching {
            rawInflater.setInput(bytes)
            val buffer = ByteArray(1024)
            val baos = ByteArrayOutputStream()
            while (!rawInflater.finished()) {
                val count = rawInflater.inflate(buffer)
                if (count == 0) {
                    if (rawInflater.needsInput() || rawInflater.needsDictionary()) break
                }
                baos.write(buffer, 0, count)
                if (baos.size() > maxBytes) return null
            }
            baos.toByteArray()
        }.getOrNull()
        rawInflater.end()

        if (rawResult != null && rawResult.isNotEmpty()) {
            return rawResult
        }

        val zlibInflater = java.util.zip.Inflater(false)
        val zlibResult = runCatching {
            zlibInflater.setInput(bytes)
            val buffer = ByteArray(1024)
            val baos = ByteArrayOutputStream()
            while (!zlibInflater.finished()) {
                val count = zlibInflater.inflate(buffer)
                if (count == 0) {
                    if (zlibInflater.needsInput() || zlibInflater.needsDictionary()) break
                }
                baos.write(buffer, 0, count)
                if (baos.size() > maxBytes) return null
            }
            baos.toByteArray()
        }.getOrNull()
        zlibInflater.end()
        return zlibResult
    }

    fun forwardToBrowser(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL.toLong()
        } else {
            PackageManager.MATCH_DEFAULT_ONLY.toLong()
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(browserIntent, PackageManager.ResolveInfoFlags.of(flags))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(browserIntent, flags.toInt())
        }

        val externalHandlers = resolveList.filter { it.activityInfo.packageName != context.packageName }
        if (externalHandlers.isEmpty()) return false

        val ownComponents = resolveList
            .filter { it.activityInfo.packageName == context.packageName }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .toTypedArray()

        val chooser = Intent.createChooser(browserIntent, "Open with").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && ownComponents.isNotEmpty()) {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ownComponents)
            }
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun openByDefaultSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        } else {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    }
}
