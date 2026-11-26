package net.hordeslop.studio.data.remote

import android.os.SystemClock
import net.hordeslop.studio.domain.HordeConstants
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class HordeRateLimiterInterceptor(
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
    private val minIntervalMsProvider: () -> Long = { effectiveMinIntervalMs(3) },
) : Interceptor {

    companion object {
        fun effectiveMinIntervalMs(seconds: Int): Long {
            val clamped = seconds.coerceIn(1, 30)
            return if (clamped == 1) 1_100L else clamped * 1_000L
        }
    }

    private val lock = Any()
    private var lastRequestElapsedMs: Long = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val minIntervalMs = minIntervalMsProvider().coerceAtLeast(1_100L)
        synchronized(lock) {
            val now = SystemClock.elapsedRealtime()
            val waitMs = (lastRequestElapsedMs + minIntervalMs) - now
            if (lastRequestElapsedMs > 0L && waitMs > 0L) {
                try {
                    Thread.sleep(waitMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestElapsedMs = SystemClock.elapsedRealtime()
        }

        val original = chain.request()
        val configuredBase = HordeConstants.normalizeBaseUrl(baseUrlProvider()).toHttpUrlOrNull()
        val rebuiltUrl = if (configuredBase != null) {
            val defaultBase = HordeConstants.DEFAULT_API_BASE_URL.toHttpUrlOrNull()
            val origUrl = original.url
            val baseSegments = configuredBase.pathSegments.filter { it.isNotEmpty() }
            val defaultSegments = defaultBase?.pathSegments?.filter { it.isNotEmpty() } ?: listOf("api", "v2")
            val relSegments = origUrl.pathSegments.drop(defaultSegments.size)

            origUrl.newBuilder()
                .scheme(configuredBase.scheme)
                .host(configuredBase.host)
                .port(configuredBase.port)
                .apply {
                    for (i in origUrl.pathSize - 1 downTo 0) {
                        removePathSegment(0)
                    }
                    baseSegments.forEach { addPathSegment(it) }
                    relSegments.forEach { addPathSegment(it) }
                }
                .build()
        } else {
            original.url
        }

        val req = original.newBuilder()
            .url(rebuiltUrl)
            .header("apikey", apiKeyProvider().ifBlank { HordeConstants.ANONYMOUS_KEY })
            .header("Client-Agent", HordeConstants.CLIENT_AGENT)
            .header("Accept", "application/json")
            .build()

        return chain.proceed(req)
    }
}
