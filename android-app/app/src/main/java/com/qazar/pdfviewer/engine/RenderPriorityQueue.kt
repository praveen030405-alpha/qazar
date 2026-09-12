package com.qazar.pdfviewer.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RenderPriority(val weight: Int) {
    JUMP_TARGET(300),        // Absolute preemptive priority: user commanded a page jump
    DEEP_ZOOM(200),          // User is actively zooming and waiting for razor-sharp text
    FAST_PREVIEW(150),       // Sub-15ms fast visual bridge for jump/navigation on preview worker
    VISIBLE(100),            // High priority: currently on screen
    VISIBLE_THUMBNAIL(90),   // Fast preview placeholder for visible page (<4ms)
    PREFETCH_ADJACENT(50),   // Pages immediately next to visible (n-1, n+1)
    PREFETCH_FAR(25),        // Pages further out (n-3, n+3)
    THUMBNAIL(10)            // Lowest priority: background skeletons
}

data class RenderTask(
    val id: String,
    val filePath: String,
    val pageIndex: Int,
    val dpi: Float,
    val viewingMode: com.qazar.pdfviewer.theme.ViewingMode,
    val priority: RenderPriority,
    val isDeepZoom: Boolean = false,
    val onResult: (android.graphics.Bitmap?) -> Unit
)

/**
 * M3 Engine: RenderPriorityQueue
 * Manages the order in which pages and tiles are rendered.
 * Bypasses standard coroutine FIFO to ensure visible pages preempt background prefetches.
 */
object RenderPriorityQueue {
    private val mainTaskQueue = java.util.PriorityQueue<RenderTask> { t1, t2 ->
        t2.priority.weight.compareTo(t1.priority.weight) // Higher weight first
    }
    
    private val previewTaskQueue = java.util.PriorityQueue<RenderTask> { t1, t2 ->
        t2.priority.weight.compareTo(t1.priority.weight)
    }

    private val taskMap = mutableMapOf<String, RenderTask>()
    private val mutex = Mutex()
    
    // Channels to signal the Scheduler that a new task is available
    val signalChannel = Channel<Unit>(Channel.CONFLATED)
    val previewSignalChannel = Channel<Unit>(Channel.CONFLATED)

    suspend fun enqueue(task: RenderTask) {
        val isPreview = (task.priority == RenderPriority.FAST_PREVIEW || 
                         task.priority == RenderPriority.VISIBLE_THUMBNAIL || 
                         task.priority == RenderPriority.THUMBNAIL)

        // Immediate cache hit check
        val cached = MemoryBudgetManager.get(task.id)
        if (cached != null) {
            task.onResult(cached)
            return
        }

        mutex.withLock {
            val lockedCached = MemoryBudgetManager.get(task.id)
            if (lockedCached != null) {
                task.onResult(lockedCached)
                return@withLock
            }

            // When a high-priority JUMP_TARGET, DEEP_ZOOM or VISIBLE task arrives, immediately evict low-priority prefetches
            if (task.priority == RenderPriority.JUMP_TARGET || 
                task.priority == RenderPriority.DEEP_ZOOM || 
                task.priority == RenderPriority.VISIBLE) {
                val stalePrefetches = mainTaskQueue.filter {
                    it.priority == RenderPriority.PREFETCH_FAR || it.priority == RenderPriority.PREFETCH_ADJACENT
                }
                mainTaskQueue.removeAll(stalePrefetches.toSet())
                stalePrefetches.forEach { taskMap.remove(it.id) }
            }

            val existing = taskMap[task.id]
            if (existing != null) {
                val prevCallback = existing.onResult
                val nextCallback = task.onResult
                val combinedCallback: (android.graphics.Bitmap?) -> Unit = { bmp ->
                    try { prevCallback(bmp) } catch (_: Throwable) {}
                    try { nextCallback(bmp) } catch (_: Throwable) {}
                }
                val higherPriority = if (task.priority.weight > existing.priority.weight) task.priority else existing.priority
                val updated = existing.copy(priority = higherPriority, onResult = combinedCallback)
                if (isPreview) {
                    previewTaskQueue.remove(existing)
                    previewTaskQueue.add(updated)
                } else {
                    mainTaskQueue.remove(existing)
                    mainTaskQueue.add(updated)
                }
                taskMap[task.id] = updated
            } else {
                if (isPreview) previewTaskQueue.add(task) else mainTaskQueue.add(task)
                taskMap[task.id] = task
            }
        }
        if (isPreview) {
            previewSignalChannel.trySend(Unit)
        } else {
            signalChannel.trySend(Unit)
        }
    }

    suspend fun cancel(id: String) {
        mutex.withLock {
            val existing = taskMap.remove(id)
            if (existing != null) {
                mainTaskQueue.remove(existing)
                previewTaskQueue.remove(existing)
            }
        }
    }

    suspend fun clearPrefetches() {
        mutex.withLock {
            val toRemove = mainTaskQueue.filter { 
                it.priority == RenderPriority.PREFETCH_ADJACENT || 
                it.priority == RenderPriority.PREFETCH_FAR 
            }
            mainTaskQueue.removeAll(toRemove.toSet())
            toRemove.forEach { taskMap.remove(it.id) }

            val toRemovePreview = previewTaskQueue.filter {
                it.priority == RenderPriority.THUMBNAIL
            }
            previewTaskQueue.removeAll(toRemovePreview.toSet())
            toRemovePreview.forEach { taskMap.remove(it.id) }
        }
    }

    suspend fun pruneStalePrefetches(centerPageIndex: Int, windowRadius: Int = 3) {
        mutex.withLock {
            val toRemoveMain = mainTaskQueue.filter { task ->
                (task.priority == RenderPriority.PREFETCH_ADJACENT || task.priority == RenderPriority.PREFETCH_FAR) &&
                kotlin.math.abs(task.pageIndex - centerPageIndex) > windowRadius
            }
            if (toRemoveMain.isNotEmpty()) {
                mainTaskQueue.removeAll(toRemoveMain.toSet())
                toRemoveMain.forEach { taskMap.remove(it.id) }
            }

            val toRemovePreview = previewTaskQueue.filter { task ->
                task.priority == RenderPriority.THUMBNAIL &&
                kotlin.math.abs(task.pageIndex - centerPageIndex) > (windowRadius + 2)
            }
            if (toRemovePreview.isNotEmpty()) {
                previewTaskQueue.removeAll(toRemovePreview.toSet())
                toRemovePreview.forEach { taskMap.remove(it.id) }
            }
        }
    }

    suspend fun popHighestPriorityTask(): RenderTask? {
        mutex.withLock {
            val task = mainTaskQueue.poll()
            if (task != null) {
                taskMap.remove(task.id)
            }
            return task
        }
    }

    suspend fun popHighestPriorityPreviewTask(): RenderTask? {
        mutex.withLock {
            val task = previewTaskQueue.poll()
            if (task != null) {
                taskMap.remove(task.id)
            }
            return task
        }
    }
    
    suspend fun isEmpty(): Boolean {
        mutex.withLock {
            return mainTaskQueue.isEmpty()
        }
    }

    suspend fun isPreviewEmpty(): Boolean {
        mutex.withLock {
            return previewTaskQueue.isEmpty()
        }
    }
}
