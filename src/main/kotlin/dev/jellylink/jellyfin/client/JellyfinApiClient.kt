package dev.jellylink.jellyfin.client

import dev.jellylink.jellyfin.config.JellyfinConfig
import dev.jellylink.jellyfin.model.JellyfinMetadata
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@Component
class JellyfinApiClient(
    private val config: JellyfinConfig,
    private val authenticator: JellyfinAuthenticator,
    private val responseParser: JellyfinResponseParser = JellyfinResponseParser(),
) {
    /**
     * Delegate to [JellyfinAuthenticator.ensureAuthenticated].
     */
    fun ensureAuthenticated(): Boolean = authenticator.ensureAuthenticated()

    /**
     * Search Jellyfin for the first audio item matching [query].
     *
     * @return parsed [JellyfinMetadata], or `null` if no result / error
     */
    fun searchFirstAudioItem(query: String): JellyfinMetadata? {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val url = StringBuilder()
            .append(config.baseUrl.trimEnd('/'))
            .append("/Items?SearchTerm=")
            .append(encodedQuery)
            .append("&IncludeItemTypes=Audio&Recursive=true&Limit=")
            .append(config.searchLimit)
            .append("&Fields=Artists,AlbumArtist,MediaSources,ImageTags")
            .toString()

        val response = executeGetWithRetry(url) ?: return null

        if (response.statusCode() !in HTTP_OK_RANGE) {
            log.error("Jellyfin search failed with status {}: {}", response.statusCode(), response.body().take(ERROR_BODY_PREVIEW_LENGTH))
            return null
        }

        val body = response.body()
        log.debug("Jellyfin search response: {}", body.take(DEBUG_BODY_PREVIEW_LENGTH))

        return responseParser.parseFirstAudioItem(body, config.baseUrl)
    }

    /**
     * Execute a GET request, retrying once on 401 (server-side token revocation).
     */
    private fun executeGetWithRetry(url: String): HttpResponse<String>? {
        val request = buildGetRequest(url) ?: return null

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == HTTP_UNAUTHORIZED) {
            log.warn("Jellyfin returned 401 — token may have been revoked, re-authenticating")
            authenticator.invalidateToken()

            if (!authenticator.ensureAuthenticated()) {
                log.error("Jellyfin re-authentication failed after 401")
                return null
            }

            val retryRequest = buildGetRequest(url) ?: return null

            response = httpClient.send(retryRequest, HttpResponse.BodyHandlers.ofString())
        }

        return response
    }

    private fun buildGetRequest(url: String): HttpRequest? {
        val token = authenticator.accessToken ?: return null

        return HttpRequest
            .newBuilder()
            .uri(java.net.URI.create(url))
            .header("X-Emby-Token", token)
            .GET()
            .build()
    }

    /**
     * Build a streaming URL for the given Jellyfin item, respecting audio quality settings.
     */
    fun buildPlaybackUrl(itemId: String): String {
        val base = config.baseUrl.trimEnd('/')
        val token = authenticator.accessToken ?: ""
        val quality = config.audioQuality.trim().uppercase()

        if (quality == "ORIGINAL") {
            return "$base/Audio/$itemId/stream?static=true&api_key=$token"
        }

        val bitrate =
            when (quality) {
                "HIGH" -> BITRATE_HIGH
                "MEDIUM" -> BITRATE_MEDIUM
                "LOW" -> BITRATE_LOW
                else -> {
                    val custom = config.audioQuality.trim().toIntOrNull()

                    if (custom != null) {
                        custom * KBPS_TO_BPS
                    } else {
                        BITRATE_HIGH
                    }
                }
            }
        val codec = config.audioCodec.trim().ifEmpty { "mp3" }

        return "$base/Audio/$itemId/stream?audioBitRate=$bitrate&audioCodec=$codec&api_key=$token"
    }

    companion object {
        private val log = LoggerFactory.getLogger(JellyfinApiClient::class.java)
        private val httpClient: HttpClient = HttpClient.newHttpClient()

        private val HTTP_OK_RANGE = 200..299
        private const val HTTP_UNAUTHORIZED = 401
        private const val ERROR_BODY_PREVIEW_LENGTH = 500
        private const val DEBUG_BODY_PREVIEW_LENGTH = 2000

        private const val BITRATE_HIGH = 320_000
        private const val BITRATE_MEDIUM = 192_000
        private const val BITRATE_LOW = 128_000
        private const val KBPS_TO_BPS = 1000
    }
}
