package com.lagradost.cloudstream.desktop.utils

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
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
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
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
        if (headers.isEmpty()) return targetUrl

        val sessionId = java.util.UUID.randomUUID().toString().take(8)
        val baseUrl = targetUrl.substringBeforeLast("/") + "/"
        sessions[sessionId] = ProxySession(targetUrl, headers, baseUrl)

        val fileName = targetUrl.substringBefore("?").substringAfterLast("/").ifEmpty { "stream.mpd" }
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

                val queryParams = parseQueryParams(exchange.requestURI.query ?: "")
                var targetUrlStr = queryParams["url"]?.let { URLDecoder.decode(it, "UTF-8") }

                if (targetUrlStr.isNullOrBlank() && session != null) {
                    // Reconstruct target URL for sub-requests (e.g. /proxy/{sessionId}/segment_1.m4s)
                    val relativeSubPath = pathParts.drop(2).joinToString("/")
                    if (relativeSubPath.isNotBlank()) {
                        val base = session.baseUrl
                        targetUrlStr = if (base.endsWith("/")) base + relativeSubPath else "$base/$relativeSubPath"
                        if (!exchange.requestURI.query.isNullOrBlank()) {
                            targetUrlStr += "?${exchange.requestURI.query}"
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

                // Pass Range header from VLC if requested
                val rangeHeader = exchange.requestHeaders.getFirst("Range")
                if (!rangeHeader.isNullOrBlank()) {
                    reqBuilder.header("Range", rangeHeader)
                }

                val response = client.newCall(reqBuilder.build()).execute()
                val responseBody = response.body

                // Copy response headers back to VLC
                response.headers.forEach { (k, v) ->
                    if (!k.equals("Transfer-Encoding", ignoreCase = true) && !k.equals("Content-Length", ignoreCase = true)) {
                        exchange.responseHeaders.add(k, v)
                    }
                }

                if (targetUrlStr.endsWith(".mpd", ignoreCase = true) || targetUrlStr.contains("/dash/", ignoreCase = true)) {
                    exchange.responseHeaders.set("Content-Type", "application/dash+xml")
                } else if (targetUrlStr.endsWith(".m3u8", ignoreCase = true)) {
                    exchange.responseHeaders.set("Content-Type", "application/vnd.apple.mpegurl")
                }
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")

                val contentLength = responseBody.contentLength()
                exchange.sendResponseHeaders(response.code, if (contentLength > 0) contentLength else 0)

                val input: InputStream = responseBody.byteStream()
                val output: OutputStream = exchange.responseBody
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
                output.close()
                input.close()
                response.close()
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
