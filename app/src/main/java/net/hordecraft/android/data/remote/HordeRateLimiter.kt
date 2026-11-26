package net.hordeslop.studio.data.remote

import android.os.SystemClock
import net.hordeslop.studio.domain.HordeConstants
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class HordeRateLimiterInterceptor(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val minIntervalMsProvider: () -> Long = { effectiveMinIntervalMs(3) },
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() },
) : Interceptor {

    companion object {
        fun effectiveMinIntervalMs(seconds: Int): Long {
            val clamped = seconds.coerceIn(1, 30)
            return if (clamped == 1) 1_100L else clamped * 1_000L
        }

        fun isFallbackEligible(configuredUrl: String): Boolean {
            return HordeConstants.normalizeBaseUrl(configuredUrl) == HordeConstants.DEFAULT_API_BASE_URL
        }

        fun shouldRetryWithFallback(statusCode: Int): Boolean {
            return statusCode >= 500 || statusCode == 408
        }

        fun rewriteBaseUrl(originalUrl: HttpUrl, targetBaseUrl: String): HttpUrl {
            val targetBase = HordeConstants.normalizeBaseUrl(targetBaseUrl).toHttpUrlOrNull() ?: return originalUrl
            val defaultBase = HordeConstants.DEFAULT_API_BASE_URL.toHttpUrlOrNull()
            val baseSegments = targetBase.pathSegments.filter { it.isNotEmpty() }
            val defaultSegments = defaultBase?.pathSegments?.filter { it.isNotEmpty() } ?: listOf("api", "v2")
            val relSegments = originalUrl.pathSegments.drop(defaultSegments.size)

            return originalUrl.newBuilder()
                .scheme(targetBase.scheme)
                .host(targetBase.host)
                .port(targetBase.port)
                .apply {
                    for (i in originalUrl.pathSize - 1 downTo 0) {
                        removePathSegment(0)
                    }
                    baseSegments.forEach { addPathSegment(it) }
                    relSegments.forEach { addPathSegment(it) }
                }
                .build()
        }
    }

    private val lock = Any()
    private var lastRequestElapsedMs: Long = 0L

    private fun paceRequest() {
        val minIntervalMs = minIntervalMsProvider().coerceAtLeast(1_100L)
        synchronized(lock) {
            val now = elapsedRealtimeProvider()
            val waitMs = (lastRequestElapsedMs + minIntervalMs) - now
            if (lastRequestElapsedMs > 0L && waitMs > 0L) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestElapsedMs = elapsedRealtimeProvider()
        }
    }

    private fun buildRequest(original: Request, baseUrl: String): Request {
        return original.newBuilder()
            .url(rewriteBaseUrl(original.url, baseUrl))
            .header("apikey", apiKeyProvider().ifBlank { HordeConstants.ANONYMOUS_KEY })
            .header("Client-Agent", HordeConstants.CLIENT_AGENT)
            .header("Accept", "application/json")
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        paceRequest()

        val original = chain.request()
        val configuredUrl = baseUrlProvider()
        val allowFallback = isFallbackEligible(configuredUrl) && original.body?.isOneShot() != true
        val primaryRequest = buildRequest(original, configuredUrl)

        val primaryResponse = try {
            chain.proceed(primaryRequest)
        } catch (ioEx: IOException) {
            if (!allowFallback) throw ioEx
            val fallbackRequest = buildRequest(original, HordeConstants.FALLBACK_API_BASE_URL)
            return chain.proceed(fallbackRequest)
        }

        if (allowFallback && shouldRetryWithFallback(primaryResponse.code)) {
            primaryResponse.close()
            val fallbackRequest = buildRequest(original, HordeConstants.FALLBACK_API_BASE_URL)
            return chain.proceed(fallbackRequest)
        }

        return primaryResponse
    }
}
