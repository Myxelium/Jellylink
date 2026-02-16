package dev.jellylink.jellyfin.audio

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.arbjerg.lavalink.api.AudioPluginInfoModifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.stereotype.Component

@Component
class JellyfinAudioPluginInfoModifier : AudioPluginInfoModifier {
    override fun modifyAudioTrackPluginInfo(track: AudioTrack): JsonObject? {
        if (track !is JellyfinAudioTrack) {
            return null
        }

        val metadata = track.info

        val map = buildMap {
            metadata.identifier.let { put("jellyfinId", JsonPrimitive(it)) }
            metadata.title?.let { put("name", JsonPrimitive(it)) }
            metadata.author?.let { put("artist", JsonPrimitive(it)) }
            track.album?.let { put("albumName", JsonPrimitive(it)) }
            metadata.length.let { put("length", JsonPrimitive(it)) }
            metadata.artworkUrl?.let { put("artistArtworkUrl", JsonPrimitive(it)) }
        }

        return if (map.isEmpty()) {
            null
        } else {
            JsonObject(map)
        }
    }
}
