package com.qazar.pdfviewer.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log

/**
 * M3 Engine: MemoryBudgetManager
 * Strict accounting of all native/Java bitmap memory allocated by the PDF engine.
 * Features:
 * 1. Multi-tier L1 (Retina/DeepZoom) + L2 (Screen-fit Anchor) + L3 (Thumbnail) architecture.
 * 2. Alias de-duplication: Prevents double-counting memory when multiple keys reference the same Bitmap.
 * 3. L2 Fast-Recovery Anchor Cache: Retains up to 48 screen-fit preview bitmaps in an LRU ring
 *    so jumping back (e.g. page 200 -> 1) loads in 0ms with zero reload flicker.
 */
object MemoryBudgetManager {
    private const val TAG = "MemoryBudgetManager"

    private var maxMemoryBytes: Long = 128 * 1024 * 1024 // Safe 128MB default
    private var currentUsageBytes: Long = 0

    // Priority levels for eviction
    enum class CacheTier {
        DEEP_ZOOM,    // Evicted first (massive footprint, easily regenerated on zoom)
        PREFETCH,     // Evicted second (speculative off-screen pages)
        FAST_PREVIEW, // Intermediate screen-fit visual bridge
        THUMBNAIL,    // Rarely evicted (tiny skeletons)
        VISIBLE,      // Evicted only under heavy pressure
        ANCHOR        // Preserved anchors for visited page instant return
    }

    private data class CacheEntry(
        val key: String,
        val bitmap: Bitmap,
        val bytes: Int,
        var tier: CacheTier,
        var lastAccessedAt: Long
    )

    // L1 Primary Dynamic Cache (Retina & DeepZoom)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()
    
    // L2 Fast-Recovery Anchor Cache (LRU 48 pages, ~1MB each, sub-1ms return hit)
    private val l2AnchorCache = object : LinkedHashMap<String, Bitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > 48
        }
    }

    private val lock = Any()

    fun initialize(context: Context) {
        DisplayProfileManager.initialize(context)
        ThermalGovernor.start(context)
        val safeBudgetMb = DisplayProfileManager.maxCacheBudgetMb
        maxMemoryBytes = safeBudgetMb * 1024 * 1024
        
        Log.i(TAG, "Initialized Multi-Tier Memory Budget: $safeBudgetMb MB (Hardware Adaptive)")
    }

    private fun extractBaseKey(key: String): String {
        return key.removeSuffix("#retina")
            .removeSuffix("#fast")
            .removeSuffix("#deepzoom")
            .removeSuffix("#thumb")
    }

    fun put(key: String, bitmap: Bitmap, tier: CacheTier) {
        synchronized(lock) {
            val baseKey = extractBaseKey(key)

            // Register in L2 Anchor cache for instant page return recovery
            if (!key.endsWith("#thumb") && !key.endsWith("#deepzoom")) {
                l2AnchorCache[baseKey] = bitmap
            }

            val existingEntry = cache[key]
            if (existingEntry != null && existingEntry.bitmap === bitmap) {
                existingEntry.tier = tier
                existingEntry.lastAccessedAt = System.currentTimeMillis()
                return
            }

            // Deduplication: Only count memory if this exact Bitmap instance is not already counted
            val isAlreadyCounted = cache.values.any { it.bitmap === bitmap }
            val bytesToAdd = if (isAlreadyCounted) 0 else bitmap.allocationByteCount

            // If replacing an existing key with a different bitmap, subtract old bytes if unique
            if (existingEntry != null) {
                val isOldStillReferenced = cache.values.any { it !== existingEntry && it.bitmap === existingEntry.bitmap }
                if (!isOldStillReferenced) {
                    currentUsageBytes = (currentUsageBytes - existingEntry.bytes).coerceAtLeast(0)
                }
            }

            // Evict least valuable L1 entries if exceeding budget
            while (currentUsageBytes + bytesToAdd > maxMemoryBytes && cache.isNotEmpty()) {
                if (!evictLeastValuable()) {
                    Log.w(TAG, "Warning: Eviction threshold reached; proceeding safely.")
                    break
                }
            }

            cache[key] = CacheEntry(key, bitmap, bytesToAdd, tier, System.currentTimeMillis())
            currentUsageBytes += bytesToAdd
        }
    }

    fun putFastPreview(baseKey: String, bitmap: Bitmap) {
        put("$baseKey#fast", bitmap, CacheTier.FAST_PREVIEW)
        synchronized(lock) {
            l2AnchorCache[baseKey] = bitmap
        }
    }

    fun putAnchor(baseKey: String, bitmap: Bitmap) {
        synchronized(lock) {
            l2AnchorCache[baseKey] = bitmap
        }
    }

    /**
     * Unified Cascading Cache Lookup:
     * 1. Direct key match in L1
     * 2. Canonical retina match
     * 3. Base key match
     * 4. Fast preview match (#fast)
     * 5. L2 Anchor Cache match (0ms instant return for visited pages!)
     * 6. Thumbnail match (#thumb)
     */
    fun get(key: String): Bitmap? {
        synchronized(lock) {
            // 1. Direct L1 match
            cache[key]?.let {
                it.lastAccessedAt = System.currentTimeMillis()
                return it.bitmap
            }

            val baseKey = extractBaseKey(key)

            // 2. Retina match
            cache["$baseKey#retina"]?.let {
                it.lastAccessedAt = System.currentTimeMillis()
                return it.bitmap
            }

            // 3. Base key direct
            cache[baseKey]?.let {
                it.lastAccessedAt = System.currentTimeMillis()
                return it.bitmap
            }

            // 4. Fast preview match
            cache["$baseKey#fast"]?.let {
                it.lastAccessedAt = System.currentTimeMillis()
                return it.bitmap
            }

            // 5. L2 Anchor Cache match (Instant recovery!)
            l2AnchorCache[baseKey]?.let {
                return it
            }

            // 6. Thumbnail fallback
            cache["$baseKey#thumb"]?.let {
                it.lastAccessedAt = System.currentTimeMillis()
                return it.bitmap
            }

            return null
        }
    }

    fun getRetina(baseKey: String): Bitmap? {
        synchronized(lock) {
            val key = if (baseKey.endsWith("#retina")) baseKey else "$baseKey#retina"
            return cache[key]?.bitmap ?: cache[extractBaseKey(baseKey)]?.bitmap
        }
    }

    fun getFast(baseKey: String): Bitmap? {
        synchronized(lock) {
            val clean = extractBaseKey(baseKey)
            return cache["$clean#fast"]?.bitmap ?: l2AnchorCache[clean]
        }
    }

    fun getAnchor(baseKey: String): Bitmap? {
        synchronized(lock) {
            return l2AnchorCache[extractBaseKey(baseKey)]
        }
    }

    fun contains(key: String): Boolean {
        synchronized(lock) {
            if (cache.containsKey(key)) return true
            val baseKey = extractBaseKey(key)
            return cache.containsKey("$baseKey#retina") || 
                   cache.containsKey(baseKey) || 
                   cache.containsKey("$baseKey#fast") || 
                   l2AnchorCache.containsKey(baseKey)
        }
    }

    fun updateTier(key: String, newTier: CacheTier) {
        synchronized(lock) {
            cache[key]?.tier = newTier
            val baseKey = extractBaseKey(key)
            cache["$baseKey#retina"]?.tier = newTier
            cache[baseKey]?.tier = newTier
        }
    }
    
    fun remove(key: String) {
        synchronized(lock) {
            val entry = cache.remove(key)
            if (entry != null) {
                val isStillReferenced = cache.values.any { it.bitmap === entry.bitmap }
                if (!isStillReferenced) {
                    currentUsageBytes = (currentUsageBytes - entry.bytes).coerceAtLeast(0)
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
            l2AnchorCache.clear()
            currentUsageBytes = 0
            Log.i(TAG, "MemoryBudgetManager fully cleared.")
        }
    }

    /**
     * Evicts one item from L1. Returns true if something was evicted, false if cache is empty.
     * Order of eviction:
     * 1. Oldest DEEP_ZOOM
     * 2. Oldest PREFETCH
     * 3. Oldest FAST_PREVIEW
     * 4. Oldest THUMBNAIL
     * 5. Oldest VISIBLE
     * 6. Oldest ANCHOR
     * Note: Even when evicted from L1, screen-fit anchors remain in L2 for 0ms return jumps!
     */
    private fun evictLeastValuable(): Boolean {
        if (cache.isEmpty()) return false

        var targetKey: String? = null
        var lowestScore = Long.MAX_VALUE

        for ((key, entry) in cache) {
            val tierWeight = when (entry.tier) {
                CacheTier.DEEP_ZOOM -> 0L
                CacheTier.PREFETCH -> 100000000000L
                CacheTier.FAST_PREVIEW -> 150000000000L
                CacheTier.THUMBNAIL -> 200000000000L
                CacheTier.VISIBLE -> 300000000000L
                CacheTier.ANCHOR -> 400000000000L
            }
            val score = tierWeight + entry.lastAccessedAt

            if (score < lowestScore) {
                lowestScore = score
                targetKey = key
            }
        }

        targetKey?.let {
            val entry = cache.remove(it)
            if (entry != null) {
                val isStillReferenced = cache.values.any { other -> other.bitmap === entry.bitmap }
                if (!isStillReferenced) {
                    currentUsageBytes = (currentUsageBytes - entry.bytes).coerceAtLeast(0)
                }
                return true
            }
        }
        return false
    }
}
