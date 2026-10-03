package com.ai.assistance.operit.core.devicebridge

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single-thread work queue with per-task cancellation. Submit returns immediately; a cancelled
 * id is skipped even if it was already queued. Cancelling a task does not stop in-flight IO —
 * callers must re-check [isLive] after blocking work returns.
 *
 * Completed ids are released in `finally`, so the cancel set cannot grow without bound.
 * Host-testable without Android.
 */
class DspSerialWorkQueue(
    private val execute: (Runnable) -> Unit
) {
    private val seq = AtomicLong(0)
    private val lock = Any()
    private val cancelled = HashSet<Long>()
    private val inflight = HashSet<Long>()

    fun submit(work: (isLive: () -> Boolean) -> Unit): Long {
        val id = seq.incrementAndGet()
        synchronized(lock) { inflight.add(id) }
        val runnable =
            Runnable {
                try {
                    if (isCancelled(id)) {
                        return@Runnable
                    }
                    work { isLive(id) }
                } finally {
                    release(id)
                }
            }
        try {
            execute(runnable)
        } catch (t: Throwable) {
            release(id)
            throw t
        }
        return id
    }

    fun cancel(id: Long) {
        synchronized(lock) {
            if (inflight.contains(id)) {
                cancelled.add(id)
            }
        }
    }

    fun isLive(id: Long): Boolean {
        synchronized(lock) {
            return inflight.contains(id) && !cancelled.contains(id)
        }
    }

    /** Ids still tracked (queued or running, including cancelled-but-not-released). */
    fun trackedCount(): Int {
        synchronized(lock) {
            return inflight.size
        }
    }

    fun cancelledCount(): Int {
        synchronized(lock) {
            return cancelled.size
        }
    }

    private fun isCancelled(id: Long): Boolean {
        synchronized(lock) {
            return cancelled.contains(id)
        }
    }

    private fun release(id: Long) {
        synchronized(lock) {
            cancelled.remove(id)
            inflight.remove(id)
        }
    }
}

/** Invalidates a posted show/start callback without touching a newer request. */
class DspGenerationGate {
    private val current = AtomicLong(0)

    fun issue(): Long = current.incrementAndGet()

    fun isLive(id: Long): Boolean = current.get() == id

    fun invalidate(id: Long) {
        current.compareAndSet(id, id + 1)
    }

    fun invalidateAll() {
        current.incrementAndGet()
    }
}

/**
 * Posted show callbacks keyed by session generation. Abandoning an old token removes only that
 * generation; a newer request stays live.
 */
class DspPostedShowRegistry {
    private val seq = AtomicLong(0)
    private val live = ConcurrentHashMap<Long, Long>()

    fun issue(generation: Long): Long {
        val id = seq.incrementAndGet()
        live[id] = generation
        return id
    }

    fun isLive(id: Long): Boolean = live.containsKey(id)

    fun invalidate(id: Long) {
        live.remove(id)
    }

    fun invalidateGeneration(generation: Long) {
        live.entries.removeIf { it.value == generation }
    }

    fun invalidateAll() {
        live.clear()
    }

    fun liveCount(): Int = live.size
}

/** Pending ASR start: stop/reset/dispose bump generation so a late prepareCapture cannot open the mic. */
class DspListenStartController {
    private val generation = AtomicLong(0)
    @Volatile private var pending = false

    fun beginStart(): Long {
        pending = true
        return generation.incrementAndGet()
    }

    fun cancelPending() {
        pending = false
        generation.incrementAndGet()
    }

    fun stillPending(token: Long): Boolean = pending && generation.get() == token
}

/**
 * VIS receiver owner. A later service attach unregisters the previous owner. Destroying an old
 * VIS must not unregister a newer VIS receiver or clear the current vis identity.
 */
class DspVisReceiverOwner {
    var currentVis: Any? = null
        private set
    var receiverOwner: Any? = null
        private set

    data class AttachResult(
        val unregisterPreviousOwner: Any?,
        val shouldRegister: Boolean
    )

    data class DetachResult(
        val unregister: Boolean,
        val clearedCurrentVis: Boolean
    )

    fun attach(newVis: Any): AttachResult {
        val previous = receiverOwner
        val needUnregister = previous != null && previous !== newVis
        currentVis = newVis
        receiverOwner = newVis
        return AttachResult(
            unregisterPreviousOwner = if (needUnregister) previous else null,
            shouldRegister = needUnregister || previous == null
        )
    }

    fun detach(owner: Any): DetachResult {
        val unregister = receiverOwner === owner
        if (unregister) {
            receiverOwner = null
        }
        val cleared = currentVis === owner
        if (cleared) {
            currentVis = null
        }
        return DetachResult(unregister = unregister, clearedCurrentVis = cleared)
    }
}

/** Real-time prefs + current VIS identity + generation checks for posted show/arm. */
object DspPostedActionPolicy {
    fun canShow(
        requestLive: Boolean,
        prefsEnabled: Boolean,
        visIdentityMatches: Boolean,
        sessionShowAllowed: Boolean
    ): Boolean {
        return requestLive && prefsEnabled && visIdentityMatches && sessionShowAllowed
    }

    fun canArm(
        prefsEnabled: Boolean,
        visPresent: Boolean,
        visReady: Boolean,
        machineAllowsArm: Boolean
    ): Boolean {
        return prefsEnabled && visPresent && visReady && machineAllowsArm
    }
}
