package net.hordeslop.studio.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import kotlinx.serialization.json.Json
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

    fun parse(uri: Uri): ParsedGenerationUrlResult {
        val urlStr = uri.toString().replace("###", "%23%23%23")
        val sanitizedUri = runCatching { Uri.parse(urlStr) }.getOrDefault(uri)
        val host = sanitizedUri.host?.lowercase().orEmpty()
        val generatorSource = when {
            host.contains("tinybots") || host.contains("artbot") -> "ArtBot"
            host.contains("aihorde") -> "AI Horde"
            host.contains("stableui") -> "StableUI"
            else -> "Web Link"
        }

        val params = mutableMapOf<String, String>()

        // 1. Collect standard query parameters via Uri
        runCatching {
            sanitizedUri.queryParameterNames.forEach { name ->
                sanitizedUri.getQueryParameter(name)?.let { params[name.lowercase()] = it }
            }
        }

        // 2. Also parse raw query/fragment string from urlStr
        val queryPart = when {
            urlStr.contains('?') -> urlStr.substringAfter('?')
            else -> ""
        }
        if (queryPart.isNotBlank()) {
            queryPart.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val k = runCatching { Uri.decode(pair.substring(0, idx)) }.getOrDefault(pair.substring(0, idx)).lowercase().trim()
                    val v = runCatching { Uri.decode(pair.substring(idx + 1)) }.getOrDefault(pair.substring(idx + 1)).trim()
                    if (!params.containsKey(k)) {
                        params[k] = v
                    }
                }
            }
        }

        // 3. Collect fragment parameters
        val fragment = sanitizedUri.fragment
        if (!fragment.isNullOrBlank()) {
            val fragmentQuery = if (fragment.contains('?')) fragment.substringAfter('?') else fragment
            fragmentQuery.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val k = runCatching { Uri.decode(pair.substring(0, idx)) }.getOrDefault(pair.substring(0, idx)).lowercase().trim()
                    val v = runCatching { Uri.decode(pair.substring(idx + 1)) }.getOrDefault(pair.substring(idx + 1)).trim()
                    if (!params.containsKey(k)) params[k] = v
                }
            }
        }

        // 4. Inspect nested JSON payloads in `share`, `request`, `params`, `create`, `data`, `json`
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

        val jsonCandidate = listOfNotNull(
            params["share"],
            params["request"],
            params["params"],
            params["create"],
            params["data"],
            params["json"],
        ).firstOrNull()

        if (jsonCandidate != null) {
            val decodedJson = runCatching {
                when {
                    jsonCandidate.trim().startsWith("{") -> jsonCandidate.trim()
                    else -> String(Base64.decode(jsonCandidate, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8).trim()
                }
            }.getOrNull()

            if (decodedJson != null && decodedJson.startsWith("{")) {
                runCatching {
                    val root = json.parseToJsonElement(decodedJson).jsonObject
                    val rawPrompt = root["prompt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (rawPrompt.contains("###")) {
                        jsonPrompt = rawPrompt.substringBefore("###").trim()
                        jsonNegPrompt = rawPrompt.substringAfter("###").trim()
                    } else {
                        jsonPrompt = rawPrompt
                        jsonNegPrompt = root["negative_prompt"]?.jsonPrimitive?.contentOrNull
                            ?: root["negative"]?.jsonPrimitive?.contentOrNull
                            ?: root["uc"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }

                    jsonModel = root["models"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                        ?: root["model"]?.jsonPrimitive?.contentOrNull

                    val pObj = root["params"]?.jsonObject ?: root
                    jsonSteps = pObj["steps"]?.jsonPrimitive?.intOrNull
                    jsonCfg = pObj["cfg_scale"]?.jsonPrimitive?.floatOrNull ?: pObj["cfg"]?.jsonPrimitive?.floatOrNull
                    jsonSampler = pObj["sampler_name"]?.jsonPrimitive?.contentOrNull ?: pObj["sampler"]?.jsonPrimitive?.contentOrNull
                    jsonScheduler = pObj["scheduler"]?.jsonPrimitive?.contentOrNull
                    jsonSeed = pObj["seed"]?.jsonPrimitive?.contentOrNull
                    jsonWidth = pObj["width"]?.jsonPrimitive?.intOrNull
                    jsonHeight = pObj["height"]?.jsonPrimitive?.intOrNull
                    jsonClipSkip = pObj["clip_skip"]?.jsonPrimitive?.intOrNull
                }
            }
        }

        // 5. Extract parameters from direct query/fragment keys
        val rawPrompt = params["prompt"] ?: params["p"] ?: params["prompt_text"] ?: jsonPrompt
        var prompt = rawPrompt.trim()
        var negativePrompt = params["negative_prompt"] ?: params["negative"] ?: params["np"] ?: params["neg_prompt"] ?: jsonNegPrompt

        if (prompt.contains("###")) {
            val parts = prompt.split("###", limit = 2)
            prompt = parts[0].trim()
            if (negativePrompt.isBlank()) {
                negativePrompt = parts[1].trim()
            }
        }

        val model = params["model"] ?: params["models"] ?: params["m"] ?: jsonModel
        val steps = params["steps"]?.toIntOrNull() ?: params["step"]?.toIntOrNull() ?: jsonSteps
        val cfg = params["cfg_scale"]?.toFloatOrNull() ?: params["cfg"]?.toFloatOrNull() ?: params["scale"]?.toFloatOrNull() ?: jsonCfg
        val sampler = params["sampler"] ?: params["sampler_name"] ?: jsonSampler
        var scheduler = params["scheduler"] ?: params["schedule"] ?: jsonScheduler
        if (scheduler == null && (params["karras"] == "true" || params["karras"] == "1")) {
            scheduler = "karras"
        }

        val seed = params["seed"] ?: jsonSeed

        var width = params["width"]?.toIntOrNull() ?: jsonWidth
        var height = params["height"]?.toIntOrNull() ?: jsonHeight
        val sizeParam = params["size"] ?: params["resolution"]
        if (sizeParam != null && sizeParam.contains('x')) {
            val w = sizeParam.substringBefore('x').trim().toIntOrNull()
            val h = sizeParam.substringAfter('x').trim().toIntOrNull()
            if (w != null && h != null) {
                width = w
                height = h
            }
        }

        val clipSkip = params["clip_skip"]?.toIntOrNull() ?: params["clip"]?.toIntOrNull() ?: jsonClipSkip

        val hasMetadata = prompt.isNotBlank() || !model.isNullOrBlank() || steps != null || seed != null

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
            rawMetadataText = urlStr,
        )

        return ParsedGenerationUrlResult(
            hasMetadata = hasMetadata,
            metadata = meta,
            originalUrl = urlStr,
        )
    }

    fun parseString(text: String): ParsedGenerationUrlResult? {
        val trimmed = text.trim()
        val sanitized = trimmed.replace("###", "%23%23%23")
        val uri = runCatching { Uri.parse(sanitized) }.getOrNull() ?: return null
        if (uri.scheme.isNullOrBlank()) return null
        return parse(uri)
    }

    fun forwardToBrowser(context: Context, url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val pm = context.packageManager
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                browserIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val externalHandler = resolveList.firstOrNull { it.activityInfo.packageName != context.packageName }
        if (externalHandler != null) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setClassName(externalHandler.activityInfo.packageName, externalHandler.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(Intent.createChooser(intent, "Open in browser"))
            }
        }
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
