package com.lagradost.cloudstream.desktop.utils

import com.lagradost.cloudstream3.app
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DesktopStreamProxy {
    private var server: HttpServer? = null
    private var port: Int = 18999
    private val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            // HTTP/2 support + connection pool for fast segment reuse
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .build()
    }

    private class ProxySession(
        val targetUrl: String,
        val headers: Map<String, String>,
        val baseUrl: String
    )

    private val sessions = ConcurrentHashMap<String, ProxySession>()

    fun startIfNeeded(): Int {
        if (server != null) return port
        synchronized(this) {
            if (server != null) return port
            try {
                val socket = InetSocketAddress("127.0.0.1", 0)
                val srv = HttpServer.create(socket, 0)
                port = srv.address.port
                srv.executor = Executors.newCachedThreadPool()
                srv.createContext("/", ProxyHandler())
                srv.start()
                server = srv
                println("DesktopStreamProxy running on http://127.0.0.1:$port")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return port
    }

    fun getProxiedUrl(targetUrl: String, headers: Map<String, String>): String {
        val currentPort = startIfNeeded()

        val sessionId = java.util.UUID.randomUUID().toString().take(8)
        val baseUrl = targetUrl.substringBeforeLast("/") + "/"
        sessions[sessionId] = ProxySession(targetUrl, headers, baseUrl)

        val fileName = targetUrl.substringBefore("?").substringAfterLast("/").ifEmpty { "stream.m3u8" }
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")

        return "http://127.0.0.1:$currentPort/proxy/$sessionId/$fileName?url=$encodedUrl"
    }

    private class ProxyHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val path = exchange.requestURI.path.removePrefix("/")
                val pathParts = path.split("/")

                // Expecting path like: /proxy/{sessionId}/{fileName} or relative paths
                if (pathParts.size < 2 || pathParts[0] != "proxy") {
                    exchange.sendResponseHeaders(404, 0)
                    exchange.responseBody.close()
                    return
                }

                val sessionId = pathParts[1]
                val session = sessions[sessionId]

                val rawQuery = exchange.requestURI.rawQuery ?: ""
                var targetUrlStr: String? = if (rawQuery.startsWith("url=") || rawQuery.contains("&url=")) {
                    val rawEncoded = rawQuery.substringAfter("url=").substringBefore("&")
                    try { URLDecoder.decode(rawEncoded, "UTF-8") } catch (_: Exception) { null }
                } else null

                if (targetUrlStr.isNullOrBlank() && session != null) {
                    // Reconstruct target URL for sub-requests (e.g. /proxy/{sessionId}/segment_1.m4s)
                    val relativeSubPath = pathParts.drop(2).joinToString("/")
                    if (relativeSubPath.isNotBlank()) {
                        val base = session.baseUrl
                        targetUrlStr = if (base.endsWith("/")) base + relativeSubPath else "$base/$relativeSubPath"
                        if (rawQuery.isNotBlank() && !rawQuery.startsWith("url=")) {
                            targetUrlStr += "?$rawQuery"
                        }
                    } else {
                        targetUrlStr = session.targetUrl
                    }
                }

                if (targetUrlStr.isNullOrBlank()) {
                    exchange.sendResponseHeaders(400, 0)
                    exchange.responseBody.close()
                    return
                }

                val reqBuilder = Request.Builder().url(targetUrlStr)

                // Inject session headers into target OkHttp request
                session?.headers?.forEach { (k, v) ->
                    reqBuilder.header(k, v)
                }

                // Inject default User-Agent if none provided (prevents Cloudflare/DDoS-Guard blocks)
                val hasUA = session?.headers?.keys?.any { it.equals("user-agent", ignoreCase = true) } == true
                if (!hasUA) {
                    reqBuilder.header("User-Agent", DEFAULT_USER_AGENT)
                }

                // Pass Range header from VLC if requested
                val rangeHeader = exchange.requestHeaders.getFirst("Range")
                if (!rangeHeader.isNullOrBlank()) {
                    reqBuilder.header("Range", rangeHeader)
                }

                // Execute synchronously using CloudStream's shared OkHttpClient instance (app.baseClient)
                // This shares cookie jar, connection pool, and SSL context without runBlocking or exception wrapping
                println("[DesktopProxy] Requesting: $targetUrlStr (Headers: ${session?.headers})")
                val response = try {
                    app.baseClient.newCall(reqBuilder.build()).execute()
                } catch (e: Exception) {
                    println("[DesktopProxy] Request FAILED for $targetUrlStr: ${e.message}")
                    throw e
                }
                println("[DesktopProxy] Response: ${response.code} ${response.message} for $targetUrlStr")
                val responseBody = response.body

                val isM3u8 = targetUrlStr.endsWith(".m3u8", ignoreCase = true) || 
                             targetUrlStr.contains(".m3u8", ignoreCase = true) ||
                             (response.headers["Content-Type"]?.contains("mpegurl", ignoreCase = true) == true)

                if (isM3u8) {
                    val bodyString = responseBody.string()
                    if (bodyString.contains("#EXTM3U")) {
                        fun resolveUrl(rawSegment: String): String {
                            val trimmed = rawSegment.trim()
                            return when {
                                trimmed.startsWith("http://", ignoreCase = true) ||
                                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                                trimmed.startsWith("/") -> {
                                    val uri = java.net.URI(targetUrlStr)
                                    "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}$trimmed"
                                }
                                else -> {
                                    val base = targetUrlStr.substringBeforeLast("/") + "/"
                                    base + trimmed
                                }
                            }
                        }

                        fun proxyUrl(rawSegment: String, fallbackName: String = "resource"): String {
                            val resolved = resolveUrl(rawSegment)
                            val fileName = resolved.substringBefore("?").substringAfterLast("/").ifEmpty { fallbackName }
                            val encodedSegmentUrl = URLEncoder.encode(resolved, "UTF-8")
                            return "http://127.0.0.1:${port}/proxy/$sessionId/$fileName?url=$encodedSegmentUrl"
                        }

                        // Regex to find URI="..." inside HLS tag attributes (e.g. EXT-X-KEY, EXT-X-MEDIA)
                        val uriAttrRegex = Regex("""URI="([^"]+)"""")

                        val lines = bodyString.lines().map { line ->
                            val trimmed = line.trim()
                            when {
                                // Non-comment segment / sub-playlist line
                                trimmed.isNotBlank() && !trimmed.startsWith("#") -> {
                                    proxyUrl(trimmed, "segment.ts")
                                }
                                // HLS tag lines with URI="..." attribute (e.g. #EXT-X-KEY, #EXT-X-MEDIA)
                                trimmed.startsWith("#") && trimmed.contains("URI=\"") -> {
                                    uriAttrRegex.replace(trimmed) { matchResult ->
                                        val originalUri = matchResult.groupValues[1]
                                        val proxied = proxyUrl(originalUri, "key")
                                        """URI="$proxied""""
                                    }
                                }
                                else -> line
                            }
                        }
                        val rewrittenBody = lines.joinToString("\n")
                        val bytes = rewrittenBody.toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                        exchange.sendResponseHeaders(response.code, bytes.size.toLong())
                        exchange.responseBody.write(bytes)
                        exchange.responseBody.flush()
                        exchange.responseBody.close()
                        response.close()
                        return
                    }
                }

                // Copy response headers back to VLC
                response.headers.forEach { (k, v) ->
                    if (!k.equals("Transfer-Encoding", ignoreCase = true) && !k.equals("Content-Length", ignoreCase = true)) {
                        exchange.responseHeaders.add(k, v)
                    }
                }

                if (targetUrlStr.endsWith(".mpd", ignoreCase = true) || targetUrlStr.contains("/dash/", ignoreCase = true)) {
                    exchange.responseHeaders.set("Content-Type", "application/dash+xml")
                } else if (isM3u8) {
                    exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                }
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

                val contentLength = responseBody.contentLength()
                // Java HttpServer: 0L = Chunked Transfer Encoding (stream body bytes), -1L = No Response Body at all
                exchange.sendResponseHeaders(response.code, if (contentLength > 0) contentLength else 0L)

                val input: InputStream = responseBody.byteStream()
                val output: OutputStream = exchange.responseBody
                try {
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                } finally {
                    output.close()
                    input.close()
                    response.close()
                }
            } catch (e: Exception) {
                try {
                    exchange.sendResponseHeaders(500, 0)
                    exchange.responseBody.close()
                } catch (_: Exception) {}
            }
        }

        private fun parseQueryParams(query: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            if (query.isBlank()) return map
            for (param in query.split("&")) {
                val pair = param.split("=", limit = 2)
                if (pair.size == 2) {
                    map[pair[0]] = pair[1]
                }
            }
            return map
        }
    }
}
