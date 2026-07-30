package com.example.apexdownloader.engine

/**
 * A concrete resolution/format request sent to Cobalt.
 *
 * This replaces the old hardcoded `videoQuality = "1080"` and gives the user
 * a real say in what gets requested -- Cobalt still resolves to its closest
 * available match and the actual result (with size, once known) is always
 * shown in the format-selection dialog afterward, so the user never sees a
 * promise that isn't backed by what was actually resolved.
 */
data class QualityPreset(
    val label: String,
    val videoQuality: String,       // matches Cobalt's videoQuality enum
    val downloadMode: String = "auto", // "auto" (video) or "audio"
    val audioFormat: String = "mp3",   // best/mp3/ogg/wav/opus
    val audioBitrate: String = "128"   // 320/256/128/96/64/8
) {
    companion object {
        val BEST = QualityPreset("Best available", videoQuality = "max")
        val P1080 = QualityPreset("1080p", videoQuality = "1080")
        val P720 = QualityPreset("720p", videoQuality = "720")
        val P480 = QualityPreset("480p (data saver)", videoQuality = "480")
        val AUDIO_MP3 = QualityPreset("Audio only (MP3 320kbps)", videoQuality = "max", downloadMode = "audio", audioFormat = "mp3", audioBitrate = "320")
        val AUDIO_OPUS = QualityPreset("Audio only (Opus)", videoQuality = "max", downloadMode = "audio", audioFormat = "opus")

        val ALL = listOf(BEST, P1080, P720, P480, AUDIO_MP3, AUDIO_OPUS)
        val DEFAULT = P1080
    }
}
