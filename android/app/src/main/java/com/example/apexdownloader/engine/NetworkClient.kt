package com.example.apexdownloader.engine

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Single shared OkHttp client for the whole app.
 *
 * Previously VideoResolver and DownloadEngine each built their own
 * `OkHttpClient.Builder().build()` with zero configuration: no timeouts (a
 * stalled server connection could hang forever with nothing surfaced to the
 * user), no shared connection pool (wasteful extra TCP/TLS handshakes), and
 * the default OkHttp User-Agent (some CDNs and self-hosted Cobalt instances
 * block or rate-limit generic HTTP client user agents).
 */
object NetworkClient {
    const val USER_AGENT = "ApexDownloader/1.0 (+https://github.com/apex-downloader)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS) // no ceiling on total call time -- large files can legitimately take a while
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .addInterceptor { chain ->
                val original = chain.request()
                val request = if (original.header("User-Agent") == null) {
                    original.newBuilder().header("User-Agent", USER_AGENT).build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .build()
    }
}
