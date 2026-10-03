package com.ai.assistance.operit.core.devicebridge

class DspWakePolicy(
    private val dedupLimit: Int = DspContract.EVENT_ID_DEDUP_LIMIT
) {
    private val recentEventIds = ArrayDeque<String>()

    fun validate(fields: WakeFields): HandoffOutcome.Rejected? {
        if (fields.keys != DspContract.WAKE_EXTRA_KEYS) {
            return reject(DspRejectReason.SCHEMA)
        }
        if (fields.schemaVersion != DspContract.SCHEMA_VERSION) {
            return reject(DspRejectReason.SCHEMA)
        }
        val eventId = fields.eventId
        if (eventId == null || !DspContract.isAllowedEventId(eventId)) {
            return reject(DspRejectReason.SCHEMA)
        }
        if (fields.keywordId != DspContract.EXPECTED_KEYWORD_ID) {
            return reject(DspRejectReason.SCHEMA)
        }
        val confidence = fields.confidence
        if (confidence == null ||
            confidence < DspContract.MIN_CONFIDENCE ||
            confidence > DspContract.MAX_CONFIDENCE
        ) {
            return reject(DspRejectReason.SCHEMA)
        }
        if (fields.captureAvailable != false) {
            return reject(DspRejectReason.SCHEMA)
        }
        if (recentEventIds.contains(eventId)) {
            return reject(DspRejectReason.DUPLICATE)
        }
        remember(eventId)
        return null
    }

    fun rememberForTest(eventId: String) {
        remember(eventId)
    }

    private fun remember(eventId: String) {
        recentEventIds.addLast(eventId)
        while (recentEventIds.size > dedupLimit) {
            recentEventIds.removeFirst()
        }
    }

    private fun reject(reason: DspRejectReason): HandoffOutcome.Rejected {
        return HandoffOutcome.Rejected(reason)
    }
}
