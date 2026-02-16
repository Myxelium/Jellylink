package dev.jellylink.jellyfin.audio

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import dev.jellylink.jellyfin.client.JellyfinApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.IOException

/**
 * Lavaplayer [AudioSourceManager] that resolves `jfsearch:<query>` identifiers
 * against a Jellyfin server.
 */
@Service
class JellyfinAudioSourceManager(
    private val apiClient: JellyfinApiClient,
) : AudioSourceManager {
    private val httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager()

    val containerRegistry: MediaContainerRegistry = MediaContainerRegistry.DEFAULT_REGISTRY

    fun getHttpInterface(): HttpInterface = httpInterfaceManager.`interface`

    override fun getSourceName(): String = "jellyfin"

    override fun loadItem(
        manager: AudioPlayerManager,
        reference: AudioReference,
    ): AudioItem? {
        val identifier = reference.identifier ?: return null

        if (!identifier.startsWith(SEARCH_PREFIX, ignoreCase = true)) {
            return null
        }

        log.info("Jellyfin source handling identifier: {}", identifier)

        if (!apiClient.ensureAuthenticated()) {
            log.error("Jellyfin authentication failed. Check baseUrl, username, and password in jellylink config.")

            return null
        }

        val query = identifier.substring(SEARCH_PREFIX.length).trim()

        if (query.isEmpty()) {
            return null
        }

        val item = apiClient.searchFirstAudioItem(query)

        if (item == null) {
            log.warn("No Jellyfin results found for query: {}", query)

            return null
        }

        log.info("Jellyfin found: {} - {} [{}]", item.artist ?: "Unknown", item.title ?: "Unknown", item.id)

        val playbackUrl = apiClient.buildPlaybackUrl(item.id)

        log.info("Jellyfin playback URL: {}", playbackUrl)

        val trackInfo =
            AudioTrackInfo(
                item.title ?: "Unknown",
                item.artist ?: "Unknown",
                item.lengthMs ?: Long.MAX_VALUE,
                item.id,
                false,
                playbackUrl,
                item.artworkUrl,
                null,
            )

        return JellyfinAudioTrack(trackInfo, item.album, this)
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean = true

    @Throws(IOException::class)
    override fun encodeTrack(
        track: AudioTrack,
        output: DataOutput,
    ) {
        val jfTrack = track as JellyfinAudioTrack
        DataFormatTools.writeNullableText(output, jfTrack.album)
    }

    @Throws(IOException::class)
    override fun decodeTrack(
        trackInfo: AudioTrackInfo,
        input: DataInput,
    ): AudioTrack {
        var album: String? = null

        if ((input as DataInputStream).available() > 0) {
            album = DataFormatTools.readNullableText(input)
        }

        return JellyfinAudioTrack(trackInfo, album, this)
    }

    override fun shutdown() {
        this.httpInterfaceManager.close()
    }

    override fun toString(): String {
        return "Jellylink - Source manager for Jellyfin by Myxelium"
    }

    companion object {
        private val log = LoggerFactory.getLogger(JellyfinAudioSourceManager::class.java)
        private const val SEARCH_PREFIX = "jfsearch:"
    }
}
