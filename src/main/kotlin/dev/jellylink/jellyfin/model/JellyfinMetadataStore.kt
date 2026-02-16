package dev.jellylink.jellyfin.model

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe metadata store with LRU eviction to prevent unbounded memory growth.
 *
 * The store maintains a maximum of [maxSize] entries. When the limit is exceeded,
 * the least recently used entries are evicted.
 */
@Component
class JellyfinMetadataStore(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    private val data = ConcurrentHashMap<String, CacheEntry>()
    private val accessOrder = ConcurrentHashMap<String, Long>()

    @Volatile
    private var accessCounter = 0L

    fun put(
        url: String,
        metadata: JellyfinMetadata,
    ) {
        data[url] = CacheEntry(metadata, System.currentTimeMillis())
        accessOrder[url] = ++accessCounter

        // Evict old entries if size limit exceeded
        if (data.size > maxSize) {
            evictLeastRecentlyUsed()
        }
    }

    fun get(url: String): JellyfinMetadata? {
        val entry = data[url] ?: return null
        accessOrder[url] = ++accessCounter // Update access time
        return entry.metadata
    }

    /**
     * Remove the least recently used entries until size is within limit.
     * Removes 10% of entries to avoid frequent evictions.
     */
    private fun evictLeastRecentlyUsed() {
        val targetSize = (maxSize * EVICTION_RATIO).toInt()
        val toRemove = data.size - targetSize

        if (toRemove <= 0) {
            return
        }

        val lruEntries = accessOrder.entries
            .sortedBy { it.value }
            .take(toRemove)
            .map { it.key }

        lruEntries.forEach { url ->
            data.remove(url)
            accessOrder.remove(url)
        }
    }

    fun clear() {
        data.clear()
        accessOrder.clear()
        accessCounter = 0L
    }

    fun size(): Int = data.size

    private data class CacheEntry(
        val metadata: JellyfinMetadata,
        val timestamp: Long,
    )

    companion object {
        private const val DEFAULT_MAX_SIZE = 10000
        private const val EVICTION_RATIO = 0.9 // Keep 90% of entries
    }
}
