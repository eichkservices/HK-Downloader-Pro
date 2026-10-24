package com.example.apexdownloader.engine

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder

/** What a single backend in the resolver chain can report back. */
sealed class ResolveOutcome {
    data class Success(val title: String, val thumbnail: String, val formats: List<VideoFormat>) : ResolveOutcome()
    /** This backend doesn't apply here (wrong platform, or not configured) -- skip silently, don't count as a failure. */
    object NotApplicable : ResolveOutcome()
    /** This backend genuinely applies and was tried, but didn't work -- counts toward the aggregated error if everything fails. */
    data class Failed(val reason: String) : ResolveOutcome()
}

/** Config each resolver might need. Not every resolver uses every field. */
data class ResolverContext(
    val desktopServerUrl: String?,
    val cobaltInstanceUrl: String?,
    val preset: QualityPreset
)

interface Resolver {
    val name: String
    suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome
}

object VideoResolver {
    private val client = NetworkClient.client

    // Types that require a real extraction backend (desktop companion app or a
    // self-hosted Cobalt instance) -- a raw HTTP GET of the page URL will never
    // return the actual media file for these. This list matches Cobalt's
    // actual supported services (verified against a live instance's own
    // service registry) -- not every video site works through Cobalt, but
    // these do.
    val videoPlatformTypes = setOf(
        "youtube", "tiktok", "reddit", "facebook", "instagram", "twitter", "bilibili",
        "bluesky", "dailymotion", "loom", "ok", "pinterest", "rutube", "snapchat",
        "soundcloud", "streamable", "tumblr", "twitch", "vimeo", "vk", "xiaohongshu"
    )

    fun identifyLinkType(url: String): String {
        return when {
            url.contains("drive.google.com") -> "google-drive"
            url.contains("dropbox.com") -> "dropbox"
            url.contains("youtube.com") || url.contains("youtu.be") -> "youtube"
            url.contains("reddit.com") -> "reddit"
            url.contains("tiktok.com") -> "tiktok"
            url.contains("facebook.com") || url.contains("fb.watch") -> "facebook"
            url.contains("instagram.com") -> "instagram"
            url.contains("twitter.com") || url.contains("x.com") -> "twitter"
            url.contains("bilibili.com") || url.contains("bilibili.tv") -> "bilibili"
            url.contains("bsky.app") -> "bluesky"
            url.contains("dailymotion.com") -> "dailymotion"
            url.contains("loom.com") -> "loom"
            url.contains("ok.ru") -> "ok"
            url.contains("pinterest.com") || url.contains("pin.it") -> "pinterest"
            url.contains("rutube.ru") -> "rutube"
            url.contains("snapchat.com") -> "snapchat"
            url.contains("soundcloud.com") -> "soundcloud"
            url.contains("streamable.com") -> "streamable"
            url.contains("tumblr.com") -> "tumblr"
            url.contains("twitch.tv") -> "twitch"
            url.contains("vimeo.com") -> "vimeo"
            url.contains("vk.com") || url.contains("vk.ru") -> "vk"
            url.contains("xiaohongshu.com") || url.contains("xhslink.com") -> "xiaohongshu"
            else -> "direct-link"
        }
    }

    // Remembers which backend most recently succeeded for a given link type,
    // so next time we try that one FIRST instead of always starting from the
    // top of the static priority list. In-memory only (resets when the app
    // process restarts) -- a real persisted version would need a Context
    // threaded through here, which is a bigger change than this one warrants
    // right now.
    private val lastSuccessfulResolver = mutableMapOf<String, String>()

    // ---- The actual chain, in static priority order per type ----
    private val cloudStorageResolver = CloudStorageResolver()
    private val tikTokPublicApiResolver = TikTokPublicApiResolver()
    private val instagramDirectResolver = InstagramDirectResolver()
    private val youtubeDirectResolver = YouTubeDirectResolver()
    private val twitterDirectResolver = TwitterDirectResolver()
    private val universalCloudResolver = UniversalCloudResolver()
    private val desktopServerResolver = DesktopServerResolver()
    private val cobaltResolver = CobaltResolver()
    private val localDirectLinkResolver = LocalDirectLinkResolver()

    private fun chainFor(type: String): List<Resolver> {
        val base = when (type) {
            "google-drive", "dropbox" -> listOf(cloudStorageResolver)
            "tiktok" -> listOf(tikTokPublicApiResolver, universalCloudResolver, cobaltResolver)
            "instagram" -> listOf(universalCloudResolver, instagramDirectResolver, cobaltResolver)
            "youtube" -> listOf(youtubeDirectResolver, universalCloudResolver, cobaltResolver)
            "twitter" -> listOf(twitterDirectResolver, universalCloudResolver, cobaltResolver)
            "direct-link" -> listOf(localDirectLinkResolver)
            in videoPlatformTypes -> listOf(universalCloudResolver, cobaltResolver)
            else -> listOf(localDirectLinkResolver)
        }
        // Always keep on-device direct resolvers first for YouTube, TikTok, and Twitter/X
        // so a one-off cloud fallback never demotes residential on-device extraction.
        if (type == "youtube" || type == "tiktok" || type == "twitter") return base
        val lastGood = lastSuccessfulResolver[type] ?: return base
        val winner = base.find { it.name == lastGood } ?: return base
        return listOf(winner) + base.filter { it.name != lastGood }
    }

    fun resolveFreshYoutubeUrl(
        videoUrlOrId: String,
        targetItag: Int? = null,
        targetHeight: Int? = null,
        isAudioOnly: Boolean = false
    ): String? {
        return youtubeDirectResolver.resolveSingleStreamUrl(videoUrlOrId, targetItag, targetHeight, isAudioOnly)
    }

    suspend fun resolveVideoInfo(
        url: String,
        desktopServerUrl: String?,
        cobaltInstanceUrl: String?,
        preset: QualityPreset = QualityPreset.DEFAULT,
        onResult: (Title: String, Thumbnail: String, Formats: List<VideoFormat>) -> Unit,
        onError: (String) -> Unit
    ) {
        val type = identifyLinkType(url)
        val ctx = ResolverContext(desktopServerUrl, cobaltInstanceUrl, preset)
        val chain = chainFor(type)

        val failures = mutableListOf<String>()
        var singleFormatFallback: ResolveOutcome.Success? = null

        for (resolver in chain) {
            when (val outcome = resolver.resolve(url, type, ctx)) {
                is ResolveOutcome.Success -> {
                    if (outcome.formats.size >= 2) {
                        lastSuccessfulResolver[type] = resolver.name
                        onResult(outcome.title, outcome.thumbnail, outcome.formats)
                        return
                    } else if (singleFormatFallback == null) {
                        singleFormatFallback = outcome
                    }
                }
                is ResolveOutcome.Failed -> failures.add("${resolver.name}: ${outcome.reason}")
                ResolveOutcome.NotApplicable -> { /* skip silently, doesn't count as a failure */ }
            }
        }

        if (singleFormatFallback != null) {
            onResult(singleFormatFallback.title, singleFormatFallback.thumbnail, singleFormatFallback.formats)
            return
        }

        if (type in videoPlatformTypes) {
            val detail = if (failures.isNotEmpty()) " (${failures.joinToString("; ")})" else ""
            onError(
                "Couldn't resolve this ${type.replaceFirstChar { it.uppercase() }} link. Please check the URL or your connection and try again.$detail"
            )
        } else if (failures.isNotEmpty()) {
            onError("Couldn't resolve this link (${failures.joinToString("; ")}).")
        }
    }

    /**
     * Best-effort file size lookup for the format picker. Tries HEAD first
     * (cheap, no body transfer); some CDNs/tunnels reject HEAD, so falls back
     * to a single-byte ranged GET and reads Content-Range's total size. Never
     * throws -- a size we can't determine just isn't shown, rather than
     * blocking the whole analyze step or showing a wrong number.
     */
    fun probeContentLength(url: String): Long? {
        try {
            val headRequest = Request.Builder().url(url).head().build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val len = response.header("Content-Length")?.toLongOrNull()
                    if (len != null && len > 0) return len
                }
            }
        } catch (e: Exception) {}

        try {
            val rangeRequest = Request.Builder().url(url).header("Range", "bytes=0-0").build()
            client.newCall(rangeRequest).execute().use { response ->
                val contentRange = response.header("Content-Range")
                val total = contentRange?.substringAfterLast('/')?.toLongOrNull()
                if (total != null && total > 0) return total
            }
        } catch (e: Exception) {}

        return null
    }

    /**
     * YouTube serves thumbnails from a public, unauthenticated CDN keyed only
     * by video ID (https://i.ytimg.com/vi/<id>/hqdefault.jpg) -- no API call
     * needed, just parsing the ID out of whichever URL shape the user pasted.
     * Returns "" for anything that doesn't look like a YouTube video URL.
     */
    fun extractYoutubeThumbnail(url: String): String {
        val id = Regex("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
        return if (id != null) "https://i.ytimg.com/vi/$id/maxresdefault.jpg" else ""
    }
}

/** Google Drive / Dropbox: not really a "resolve against a backend" case --
 *  just builds a direct-download URL, so it can't meaningfully fail. */
private class CloudStorageResolver : Resolver {
    override val name = "cloud-storage"
    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "google-drive" && type != "dropbox") return ResolveOutcome.NotApplicable

        val isFolder = url.contains("/folders/") || url.contains("/sh/")
        var title = if (type == "google-drive") {
            if (isFolder) "Google Drive Folder" else "Google Drive File"
        } else {
            if (isFolder) "Dropbox Folder" else "Dropbox File"
        }
        val thumbnail = if (type == "google-drive") {
            "https://upload.wikimedia.org/wikipedia/commons/1/12/Google_Drive_icon_%282020%29.svg"
        } else {
            "https://upload.wikimedia.org/wikipedia/commons/7/78/Dropbox_Icon.svg"
        }

        if (type == "dropbox") {
            try {
                val pathSegments = URL(url).path.split("/")
                val decodedName = URLDecoder.decode(pathSegments.last(), "UTF-8").split("?")[0]
                if (decodedName.isNotEmpty()) title = decodedName
            } catch (e: Exception) {}
        }

        val formats = listOf(
            VideoFormat("original", "Original Quality (Direct Link)", "direct", sizeBytes = VideoResolver.probeContentLength(url))
        )
        return ResolveOutcome.Success(title, thumbnail, formats)
    }
}

/** Direct on-device YouTube extraction via YouTube's IOS & Android player APIs.
 *  Uses the device's own residential/carrier IP to cleanly resolve full 4K/1080p/720p/480p/360p/Audio streams. */
private class YouTubeDirectResolver : Resolver {
    override val name = "youtube-direct"
    private val client = NetworkClient.client

    private data class InnertubeClientConfig(
        val name: String,
        val version: String,
        val clientNameId: String,
        val ua: String,
        val isEmbedded: Boolean = false,
        val makeContext: () -> JSONObject
    )

    private data class WatchSession(
        val visitorData: String,
        val cookieHeader: String,
        val sts: Int
    )

    private fun fetchWatchVisitorSession(vidId: String): WatchSession {
        val cookiePairs = mutableListOf("PREF=hl=en&tz=UTC", "SOCS=CAI")
        var visitorData = ""
        var sts = 20746
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/watch?v=$vidId&bpctr=9999999999&has_verified=1")
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", "PREF=hl=en&tz=UTC; SOCS=CAI")
                .build()
            client.newCall(req).execute().use { resp ->
                for (sc in resp.headers("Set-Cookie")) {
                    val pair = sc.substringBefore(';').trim()
                    if (pair.isNotEmpty()) cookiePairs.add(pair)
                }
                val html = resp.body?.string() ?: ""
                Regex("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)?.let {
                    visitorData = it
                }
                Regex("\"STS\"\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toIntOrNull()?.let {
                    sts = it
                }
            }
        } catch (e: Exception) {}
        return WatchSession(visitorData, cookiePairs.distinct().joinToString("; "), sts)
    }

    private fun getInnertubeClients(): List<InnertubeClientConfig> {
        val visionUa = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        return listOf(
            InnertubeClientConfig(
                name = "VISIONOS",
                version = "1.02",
                clientNameId = "101",
                ua = visionUa,
                makeContext = {
                    JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "RealityDevice17,1")
                        put("userAgent", visionUa)
                        put("osName", "visionOS")
                        put("osVersion", "26.5.23O471")
                        put("hl", "en")
                        put("timeZone", "UTC")
                        put("utcOffsetMinutes", 0)
                    }
                }
            ),
            InnertubeClientConfig(
                name = "ANDROID",
                version = "20.10.38",
                clientNameId = "3",
                ua = "com.google.android.youtube/20.10.38 (Linux; U; Android 14; en_US) gzip",
                makeContext = {
                    JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "20.10.38")
                        put("androidSdkVersion", 34)
                        put("osName", "Android")
                        put("osVersion", "14")
                        put("gl", "US")
                        put("hl", "en")
                    }
                }
            ),
            InnertubeClientConfig(
                name = "IOS",
                version = "20.03.02",
                clientNameId = "5",
                ua = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
                makeContext = {
                    JSONObject().apply {
                        put("clientName", "IOS")
                        put("clientVersion", "20.03.02")
                        put("deviceMake", "Apple")
                        put("deviceModel", "iPhone16,2")
                        put("osName", "iPhone")
                        put("osVersion", "18.2.1.22C161")
                        put("gl", "US")
                        put("hl", "en")
                    }
                }
            )
        )
    }

    private fun parseNormalizedTier(f: JSONObject): Int {
        val qLabel = f.optString("qualityLabel", "")
        val labelMatch = Regex("(\\d{3,4})p").find(qLabel)?.groupValues?.get(1)?.toIntOrNull()
        if (labelMatch != null && labelMatch > 0) return labelMatch

        val w = f.optInt("width", 0)
        val h = f.optInt("height", 0)
        if (w > 0 && h > 0) return minOf(w, h)
        return h
    }

    fun resolveSingleStreamUrl(
        videoUrlOrId: String,
        targetItag: Int? = null,
        targetHeight: Int? = null,
        isAudioOnly: Boolean = false
    ): String? {
        val vidId = Regex("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})").find(videoUrlOrId)?.groupValues?.get(1)
            ?: videoUrlOrId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{11}")) }
            ?: return null

        val session = fetchWatchVisitorSession(vidId)
        for (c in getInnertubeClients()) {
            try {
                val payload = JSONObject().apply {
                    put("videoId", vidId)
                    put("context", JSONObject().apply { put("client", c.makeContext()) })
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("signatureTimestamp", session.sts)
                        })
                    })
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }.toString()

                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", c.clientNameId)
                    .header("X-YouTube-Client-Version", c.version)
                    .header("Origin", "https://www.youtube.com")
                    .header("User-Agent", c.ua)
                    .header("Cookie", session.cookieHeader)
                if (session.visitorData.isNotEmpty()) {
                    reqBuilder.header("X-Goog-Visitor-Id", session.visitorData)
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val data = JSONObject(response.body?.string() ?: "")
                    if (data.optJSONObject("playabilityStatus")?.optString("status") != "OK") return@use
                    val streamingData = data.optJSONObject("streamingData") ?: return@use
                    val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
                    val rawFormats = streamingData.optJSONArray("formats") ?: JSONArray()

                    if (targetItag != null) {
                        for (i in 0 until adaptive.length()) {
                            val af = adaptive.getJSONObject(i)
                            if (af.optInt("itag") == targetItag && af.optString("url").isNotEmpty()) {
                                return af.optString("url")
                            }
                        }
                        for (i in 0 until rawFormats.length()) {
                            val rf = rawFormats.getJSONObject(i)
                            if (rf.optInt("itag") == targetItag && rf.optString("url").isNotEmpty()) {
                                return rf.optString("url")
                            }
                        }
                    }

                    if (isAudioOnly) {
                        var bestUrl: String? = null
                        var bestScore = -1
                        for (i in 0 until adaptive.length()) {
                            val af = adaptive.getJSONObject(i)
                            val u = af.optString("url")
                            val mime = af.optString("mimeType")
                            if (u.isNotEmpty() && mime.contains("audio")) {
                                val score = af.optInt("bitrate", 0) +
                                    (if (mime.contains("mp4")) 1_000_000 else 0) +
                                    (if (!af.optBoolean("isDrc", false)) 100_000 else 0)
                                if (score > bestScore) {
                                    bestScore = score
                                    bestUrl = u
                                }
                            }
                        }
                        if (bestUrl != null) return bestUrl
                    }

                    if (targetHeight != null) {
                        for (i in 0 until adaptive.length()) {
                            val af = adaptive.getJSONObject(i)
                            val u = af.optString("url")
                            val mime = af.optString("mimeType")
                            if (u.isNotEmpty() && mime.contains("video") && mime.contains("avc1") && parseNormalizedTier(af) == targetHeight) {
                                return u
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return null
    }

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "youtube") return ResolveOutcome.NotApplicable
        val vidId = Regex("(?:v=|youtu\\.be/|shorts/|embed/)([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.get(1)
            ?: return ResolveOutcome.Failed("Invalid YouTube URL")

        val session = fetchWatchVisitorSession(vidId)
        val clients = getInnertubeClients()

        var lastErr = "Video unplayable"
        var fallbackSingleOutcome: ResolveOutcome.Success? = null

        for (c in clients) {
            try {
                val payload = JSONObject().apply {
                    put("videoId", vidId)
                    put("context", JSONObject().apply {
                        put("client", c.makeContext())
                    })
                    put("playbackContext", JSONObject().apply {
                        put("contentPlaybackContext", JSONObject().apply {
                            put("html5Preference", "HTML5_PREF_WANTS")
                            put("signatureTimestamp", session.sts)
                        })
                    })
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }.toString()

                val requestBody = payload.toRequestBody("application/json".toMediaTypeOrNull())
                val reqBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", c.clientNameId)
                    .header("X-YouTube-Client-Version", c.version)
                    .header("Origin", "https://www.youtube.com")
                    .header("User-Agent", c.ua)
                    .header("Cookie", session.cookieHeader)
                if (session.visitorData.isNotEmpty()) {
                    reqBuilder.header("X-Goog-Visitor-Id", session.visitorData)
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastErr = "HTTP ${response.code}"
                        return@use
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    val playability = data.optJSONObject("playabilityStatus")
                    if (playability?.optString("status") != "OK") {
                        lastErr = playability?.optString("reason") ?: "Video unplayable"
                        return@use
                    }

                    val title = data.optJSONObject("videoDetails")?.optString("title", "YouTube Video") ?: "YouTube Video"
                    var thumbnail = "https://i.ytimg.com/vi/$vidId/maxresdefault.jpg"
                    val thumbsArr = data.optJSONObject("videoDetails")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbsArr != null && thumbsArr.length() > 0) {
                        thumbnail = thumbsArr.getJSONObject(thumbsArr.length() - 1).optString("url", thumbnail)
                    }
                    val streamingData = data.optJSONObject("streamingData")
                    val rawFormats = streamingData?.optJSONArray("formats") ?: JSONArray()
                    val adaptive = streamingData?.optJSONArray("adaptiveFormats") ?: JSONArray()
                    val formats = mutableListOf<VideoFormat>()

                    var bestAudioUrl: String? = null
                    var bestAudioClen: Long? = null
                    var highestAudioScore = -1
                    for (i in 0 until adaptive.length()) {
                        val af = adaptive.getJSONObject(i)
                        val streamUrl = af.optString("url")
                        val mime = af.optString("mimeType")
                        if (streamUrl.isNotEmpty() && mime.contains("audio")) {
                            val bitrate = af.optInt("bitrate", af.optInt("averageBitrate", 0))
                            val isMp4Audio = mime.contains("mp4") || mime.contains("m4a")
                            val isNotDrc = !af.optBoolean("isDrc", false)
                            // Strongly prefer non-DRC MP4/AAC audio so Android MediaMuxer can mux it natively with H.264 video
                            val score = bitrate + (if (isMp4Audio) 1_000_000 else 0) + (if (isNotDrc) 100_000 else 0)
                            if (score > highestAudioScore || bestAudioUrl == null) {
                                highestAudioScore = score
                                bestAudioUrl = streamUrl
                                bestAudioClen = af.optLong("contentLength", -1L).takeIf { it > 0 }
                            }
                        }
                    }

                    data class YtStream(
                        val url: String,
                        val tier: Int,
                        val ext: String,
                        val hasMuxedAudio: Boolean,
                        val isAvcMp4: Boolean,
                        val clen: Long?
                    )
                    val videoMap = mutableMapOf<Int, YtStream>()

                    // 1. Progressive streams (muxed audio)
                    for (i in 0 until rawFormats.length()) {
                        val f = rawFormats.getJSONObject(i)
                        val streamUrl = f.optString("url")
                        val tier = parseNormalizedTier(f)
                        if (streamUrl.isNotEmpty() && tier > 0) {
                            val clen = f.optLong("contentLength", -1L).takeIf { it > 0 }
                            videoMap[tier] = YtStream(streamUrl, tier, "mp4", true, true, clen)
                        }
                    }

                    // 2. Adaptive streams (video only)
                    for (i in 0 until adaptive.length()) {
                        val af = adaptive.getJSONObject(i)
                        val streamUrl = af.optString("url")
                        val mime = af.optString("mimeType")
                        if (streamUrl.isEmpty() || !mime.contains("video")) continue
                        val tier = parseNormalizedTier(af)
                        if (tier <= 0) continue

                        val isMp4 = mime.contains("mp4")
                        val isAvc = mime.contains("avc1")
                        val existing = videoMap[tier]
                        // Prefer H.264 MP4 (avc1) for universal hardware compatibility & MediaMuxer support
                        val shouldReplace = existing == null ||
                            (!existing.hasMuxedAudio && isMp4 && (!existing.isAvcMp4 && isAvc)) ||
                            (!existing.hasMuxedAudio && existing.ext != "mp4" && isMp4)
                        if (shouldReplace) {
                            val clen = af.optLong("contentLength", -1L).takeIf { it > 0 }
                            val totalClen = if (clen != null && bestAudioClen != null) clen + bestAudioClen else clen
                            videoMap[tier] = YtStream(
                                url = streamUrl,
                                tier = tier,
                                ext = if (isMp4) "mp4" else "webm",
                                hasMuxedAudio = false,
                                isAvcMp4 = isMp4 && isAvc,
                                clen = totalClen
                            )
                        }
                    }

                    val availableTiers = videoMap.keys.sortedDescending()
                    if (availableTiers.isEmpty()) return@use

                    val maxH = availableTiers.first()

                    // 1. 4K / 2K Original Quality (if > 1080p)
                    if (maxH > 1080) {
                        val vf = videoMap[maxH]!!
                        val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                        val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                        val qLabel = if (maxH >= 2160) "4K UHD" else (if (maxH >= 1440) "2K QHD" else "${maxH}p")
                        formats.add(VideoFormat(tok, "🎬 $qLabel (${maxH}p Original Quality) · MP4", "mp4", vf.clen))
                    }

                    // 2. 1080p Full HD
                    videoMap[1080]?.let { vf ->
                        val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                        val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                        val noteStr = if (maxH == 1080) "🎬 1080p FHD (Original Quality) · MP4" else "🎬 1080p Full HD · MP4"
                        formats.add(VideoFormat(tok, noteStr, "mp4", vf.clen))
                    }

                    // 3. 720p HD
                    videoMap[720]?.let { vf ->
                        val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                        val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                        val noteStr = if (maxH == 720) "🎬 720p HD (Original Quality) · MP4" else "🎬 720p HD · MP4"
                        formats.add(VideoFormat(tok, noteStr, "mp4", vf.clen))
                    }

                    // 4. 480p SD
                    videoMap[480]?.let { vf ->
                        val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                        val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                        val noteStr = if (maxH == 480) "🎬 480p SD (Original Quality) · MP4" else "🎬 480p SD · MP4"
                        formats.add(VideoFormat(tok, noteStr, "mp4", vf.clen))
                    }

                    // 5. 360p Standard
                    videoMap[360]?.let { vf ->
                        val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                        val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                        val noteStr = if (maxH == 360) "🎬 360p (Original Quality) · MP4" else "🎬 360p Standard · MP4"
                        formats.add(VideoFormat(tok, noteStr, "mp4", vf.clen))
                    }

                    // 6. 240p Data Saver (if fewer than 4 video options so far)
                    if (formats.size < 4) {
                        videoMap[240]?.let { vf ->
                            val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                            val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                            formats.add(VideoFormat(tok, "🎬 240p Data Saver · MP4", "mp4", vf.clen))
                        }
                    }

                    // Fallback if none of standard tiers matched
                    if (formats.isEmpty() && availableTiers.isNotEmpty()) {
                        for (h in availableTiers.take(4)) {
                            val vf = videoMap[h]!!
                            val audioU = if (vf.hasMuxedAudio) null else bestAudioUrl
                            val tok = if (audioU != null) "${vf.url}|$audioU" else vf.url
                            formats.add(VideoFormat(tok, "🎬 ${h}p · MP4", "mp4", vf.clen))
                        }
                    }

                    // 7. High Quality Audio Track
                    if (bestAudioUrl != null) {
                        formats.add(VideoFormat(bestAudioUrl, "🎵 High Quality Audio (M4A / MP3)", "m4a", bestAudioClen))
                    }

                    if (formats.size >= 3) {
                        return ResolveOutcome.Success(title, thumbnail, formats)
                    } else if (formats.isNotEmpty() && fallbackSingleOutcome == null) {
                        fallbackSingleOutcome = ResolveOutcome.Success(title, thumbnail, formats)
                    }
                }
            } catch (e: Exception) {
                lastErr = e.message ?: "network error"
            }
        }
        return fallbackSingleOutcome ?: ResolveOutcome.Failed(lastErr)
    }
}

/** TikTok via the public tikwm.com API -- returns 1080p FHD, 720p HD, 480p SD, and MP3 Audio. */
private class TikTokPublicApiResolver : Resolver {
    override val name = "tikwm"
    private val client = NetworkClient.client

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "tiktok") return ResolveOutcome.NotApplicable
        return try {
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/?url=${java.net.URLEncoder.encode(url, "UTF-8")}&hd=1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolveOutcome.Failed("HTTP ${response.code}")
                val data = JSONObject(response.body?.string() ?: "")
                if (data.optInt("code", -1) != 0) return ResolveOutcome.Failed("API returned an error")
                val v = data.optJSONObject("data") ?: return ResolveOutcome.Failed("empty response")
                val title = v.optString("title", "TikTok Video")
                val thumbnail = v.optString("cover", "")
                val hdSize = v.optLong("hd_size", -1L).takeIf { it > 0 }
                val playSize = v.optLong("size", -1L).takeIf { it > 0 }
                val wmSize = v.optLong("wm_size", -1L).takeIf { it > 0 }

                val formats = mutableListOf<VideoFormat>()
                v.optString("hdplay", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "🎬 1080p Full HD (No Watermark)", "mp4", hdSize))
                }
                v.optString("play", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "🎬 720p HD (No Watermark)", "mp4", playSize))
                }
                v.optString("wmplay", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "🎬 480p SD (Watermarked)", "mp4", wmSize))
                }
                v.optString("music", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "🎵 High Quality Audio (MP3)", "mp3"))
                }
                if (formats.isEmpty()) ResolveOutcome.Failed("no playable formats in response")
                else ResolveOutcome.Success(title, thumbnail, formats)
            }
        } catch (e: Exception) {
            ResolveOutcome.Failed(e.message ?: "network error")
        }
    }
}

/** The user's own desktop companion app, if configured in Settings. */
private class DesktopServerResolver : Resolver {
    override val name = "desktop-server"
    private val client = NetworkClient.client

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (ctx.desktopServerUrl.isNullOrEmpty()) return ResolveOutcome.NotApplicable
        return try {
            val cleanServerUrl = ctx.desktopServerUrl.trimEnd('/')
            val jsonReq = JSONObject().apply {
                put("url", url)
                put("preferredQuality", ctx.preset.videoQuality)
                put("preferredMode", ctx.preset.downloadMode)
            }.toString()
            val requestBody = jsonReq.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url("$cleanServerUrl/api/info").post(requestBody).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolveOutcome.Failed("HTTP ${response.code}")
                val data = JSONObject(response.body?.string() ?: "")
                val title = data.optString("title", "Universal Video")
                val thumbnail = data.optString("thumbnail", "")

                val formatsList = mutableListOf<VideoFormat>()
                val formatsArr = data.optJSONArray("formats")
                if (formatsArr != null) {
                    for (i in 0 until formatsArr.length()) {
                        val fObj = formatsArr.getJSONObject(i)
                        val formatId = fObj.optString("formatId")
                        val ext = fObj.optString("ext")
                        val resolution = fObj.optString("resolution")
                        val note = fObj.optString("note")
                        val sizeBytes = fObj.optLong("filesize", -1L).takeIf { it > 0 }
                            ?: fObj.optLong("filesize_approx", -1L).takeIf { it > 0 }
                        formatsList.add(VideoFormat(formatId, "$resolution · $note ($ext)", ext, sizeBytes))
                    }
                }
                if (formatsList.isEmpty()) {
                    formatsList.add(VideoFormat("best", "MP4 · Best Quality", "mp4"))
                    formatsList.add(VideoFormat("audio-mp3", "MP3 · Audio Quality", "mp3"))
                }
                ResolveOutcome.Success(title, thumbnail, formatsList)
            }
        } catch (e: Exception) {
            ResolveOutcome.Failed(e.message ?: "unreachable")
        }
    }
}

private class CobaltResolver : Resolver {
    override val name = "cobalt"
    private val client = NetworkClient.client

    private val staticFallbacks = listOf(
        "https://api.cobalt.liubquanti.click",
        "https://apicobalt.mgytr.top",
        "https://subito-c.meowing.de",
        "https://lime.clxxped.lol"
    )

    private fun buildRequest(url: String, cleanCobaltUrl: String, ctx: ResolverContext): Request {
        val payload = JSONObject().apply {
            put("url", url)
            put("videoQuality", ctx.preset.videoQuality)
            put("downloadMode", ctx.preset.downloadMode)
            if (ctx.preset.downloadMode == "audio") {
                put("audioFormat", ctx.preset.audioFormat)
                put("audioBitrate", ctx.preset.audioBitrate)
            }
        }.toString()
        val requestBody = payload.toRequestBody("application/json".toMediaTypeOrNull())
        return Request.Builder()
            .url("$cleanCobaltUrl/")
            .post(requestBody)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
    }

    private suspend fun resolveSingle(url: String, type: String, cleanCobaltUrl: String, ctx: ResolverContext): ResolveOutcome {
        val request = buildRequest(url, cleanCobaltUrl, ctx)
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ResolveOutcome.Failed("HTTP ${response.code}")
            val data = JSONObject(response.body?.string() ?: "")
            when (data.optString("status")) {
                "tunnel", "redirect" -> {
                    val directUrl = data.getString("url")
                    val filename = data.optString("filename", "Video - ${type.replaceFirstChar { it.uppercase() }}")
                    val ext = filename.substringAfterLast('.', "mp4")
                    val thumbnail = if (type == "youtube") VideoResolver.extractYoutubeThumbnail(url) else ""
                    ResolveOutcome.Success(
                        filename.substringBeforeLast('.'),
                        thumbnail,
                        listOf(VideoFormat("cobalt|$directUrl", "Download ($ext)", ext, sizeBytes = VideoResolver.probeContentLength(directUrl)))
                    )
                }
                "picker" -> {
                    val pickerArr = data.optJSONArray("picker") ?: JSONArray()
                    val formats = mutableListOf<VideoFormat>()
                    for (i in 0 until pickerArr.length()) {
                        val pObj = pickerArr.getJSONObject(i)
                        val itemUrl = pObj.optString("url")
                        val itemType = pObj.optString("type", "video")
                        val ext = if (itemType == "photo") "jpg" else "mp4"
                        if (itemUrl.isNotEmpty()) {
                            val size = if (i < 10) VideoResolver.probeContentLength(itemUrl) else null
                            formats.add(VideoFormat("cobalt|$itemUrl", "Item ${i + 1} ($itemType)", ext, size))
                        }
                    }
                    if (formats.isNotEmpty()) ResolveOutcome.Success("Media Set - ${type.replaceFirstChar { it.uppercase() }}", "", formats)
                    else ResolveOutcome.Failed("picker returned no usable items")
                }
                "local-processing" -> ResolveOutcome.Failed("needs local remuxing this instance doesn't support")
                "error" -> ResolveOutcome.Failed(data.optJSONObject("error")?.optString("code") ?: "unknown error")
                else -> ResolveOutcome.Failed("unexpected response")
            }
        }
    }

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        val uniqueInstances = mutableListOf<String>()
        if (!ctx.cobaltInstanceUrl.isNullOrEmpty()) {
            uniqueInstances.add(ctx.cobaltInstanceUrl)
        } else {
            return ResolveOutcome.NotApplicable
        }

        val failures = mutableListOf<String>()
        for (instance in uniqueInstances) {
            val cleanCobaltUrl = instance.trimEnd('/')
            try {
                when (val outcome = resolveSingle(url, type, cleanCobaltUrl, ctx)) {
                    is ResolveOutcome.Success -> return outcome
                    is ResolveOutcome.Failed -> failures.add("$cleanCobaltUrl: ${outcome.reason}")
                    else -> {}
                }
            } catch (e: Exception) {
                failures.add("$cleanCobaltUrl: ${e.message ?: "error"}")
            }
        }
        return ResolveOutcome.Failed("All Cobalt instances failed: ${failures.joinToString("; ")}")
    }
}

/** Final fallback for genuine direct-link URLs (zip, rar, pdf, or any other
 *  direct file that isn't a recognized video platform). */
private class LocalDirectLinkResolver : Resolver {
    override val name = "direct-link"
    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        var title = "File"
        var ext = "bin"
        try {
            val u = URL(url)
            val pathSegments = u.path.split("/")
            val lastSegment = pathSegments.lastOrNull { it.isNotEmpty() }
            if (lastSegment != null) {
                title = URLDecoder.decode(lastSegment, "UTF-8").split("?")[0]
                if (title.contains(".")) ext = title.substringAfterLast(".")
            }
        } catch (e: Exception) {}

        val formats = listOf(VideoFormat("local-best", "Original file (.$ext)", ext, sizeBytes = VideoResolver.probeContentLength(url)))
        return ResolveOutcome.Success(title, "", formats)
    }
}

/** Resolves video links through the primary Vercel universal backend and Cloudflare Pages fallback. */
private class UniversalCloudResolver : Resolver {
    override val name = "universal-cloud"
    private val client = NetworkClient.client

    private val endpoints = listOf(
        "https://hk-downloader-pro.pages.dev/api/analyze",
        "https://hk-downloader-pro.vercel.app/api/analyze",
        "https://hkdownloader.online/api/analyze"
    )

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        var lastErr = "unreachable"
        for (endpoint in endpoints) {
            try {
                val jsonReq = JSONObject().apply {
                    put("inputUrl", url)
                    put("url", url)
                }.toString()
                val requestBody = jsonReq.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastErr = "HTTP ${response.code} from $endpoint"
                        return@use
                    }
                    val data = JSONObject(response.body?.string() ?: "")
                    val title = data.optString("title", "Universal Video")
                    val thumbnail = data.optString("thumbnail", "")

                    val formatsList = mutableListOf<VideoFormat>()
                    val formatsArr = data.optJSONArray("formats")
                    if (formatsArr != null) {
                        for (i in 0 until formatsArr.length()) {
                            val fObj = formatsArr.getJSONObject(i)
                            val directUrl = fObj.optString("directUrl")
                            val token = fObj.optString("token")
                            val ext = fObj.optString("ext", "mp4")
                            val note = fObj.optString("note", "Download")
                            val sizeBytes = fObj.optLong("sizeBytes", -1L).takeIf { it > 0 }
                                ?: fObj.optLong("size", -1L).takeIf { it > 0 }

                            val resolvedUrl = when {
                                token.contains("|http") -> token
                                directUrl.isNotEmpty() -> directUrl
                                token.startsWith("http") -> token
                                token.isNotEmpty() -> "https://hk-downloader-pro.pages.dev/api/download?url=${java.net.URLEncoder.encode(token, "UTF-8")}"
                                else -> ""
                            }

                            if (resolvedUrl.isNotEmpty()) {
                                formatsList.add(VideoFormat(resolvedUrl, note, ext, sizeBytes = sizeBytes))
                            }
                        }
                    }
                    if (formatsList.isNotEmpty()) {
                        return ResolveOutcome.Success(title, thumbnail, formatsList)
                    }
                }
            } catch (e: Exception) {
                lastErr = e.message ?: "network error"
            }
        }
        return ResolveOutcome.Failed(lastErr)
    }
}

/** Resolves Instagram reels/posts directly on-device using residential IP and mobile headers. */
private class InstagramDirectResolver : Resolver {
    override val name = "instagram-direct"
    private val client = NetworkClient.client

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "instagram") return ResolveOutcome.NotApplicable

        val shortcode = Regex("(?:/p/|/reel/|/reels/|/tv/)([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)
            ?: return ResolveOutcome.NotApplicable

        val endpoints = listOf(
            "https://www.instagram.com/p/$shortcode/?__a=1&__d=dis",
            "https://www.instagram.com/reel/$shortcode/?__a=1&__d=dis",
            "https://www.instagram.com/p/$shortcode/embed/captioned/"
        )

        for (endpoint in endpoints) {
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("X-IG-App-ID", "936619743392459")
                    .header("Accept", "*/*")
                    .header("Referer", "https://www.instagram.com/")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use

                    if (endpoint.contains("embed/captioned")) {
                        val videoMatch = Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)
                            ?: Regex("video_url&quot;:&quot;([^&]+)&quot;").find(bodyStr)
                            ?: Regex("src=\"([^\"]+\\.mp4[^\"]*)\"").find(bodyStr)
                        val thumbMatch = Regex("\"display_url\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)
                            ?: Regex("property=\"og:image\"\\s+content=\"([^\"]+)\"").find(bodyStr)

                        if (videoMatch != null) {
                            val cleanVideoUrl = videoMatch.groupValues[1]
                                .replace("\\u0026", "&")
                                .replace("&amp;", "&")
                                .replace("\\", "")
                            val cleanThumb = thumbMatch?.groupValues?.get(1)
                                ?.replace("\\u0026", "&")
                                ?.replace("&amp;", "&")
                                ?.replace("\\", "") ?: ""

                            val formats = mutableListOf(
                                VideoFormat(cleanVideoUrl, "🎬 HD Video with Audio (MP4)", "mp4", VideoResolver.probeContentLength(cleanVideoUrl)),
                                VideoFormat(cleanVideoUrl, "🎵 High Quality Audio (MP3)", "mp3")
                            )
                            return ResolveOutcome.Success("Instagram Reel", cleanThumb, formats)
                        }
                    } else {
                        val json = JSONObject(bodyStr)
                        val items = json.optJSONArray("items")
                        val item = items?.optJSONObject(0)
                        val media = item ?: json.optJSONObject("graphql")?.optJSONObject("shortcode_media")

                        if (media != null) {
                            val videoVersions = media.optJSONArray("video_versions")
                            val caption = media.optJSONObject("caption")?.optString("text")
                                ?: media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)?.optJSONObject("node")?.optString("text")
                                ?: "Instagram Reel"

                            val thumb = media.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url")
                                ?: media.optString("display_url")

                            val formats = mutableListOf<VideoFormat>()
                            if (videoVersions != null && videoVersions.length() > 0) {
                                for (i in 0 until minOf(3, videoVersions.length())) {
                                    val vObj = videoVersions.getJSONObject(i)
                                    val vUrl = vObj.optString("url")
                                    val w = vObj.optInt("width", 0)
                                    val h = vObj.optInt("height", 0)
                                    val tier = minOf(w, h).takeIf { it > 0 } ?: if (i == 0) 1080 else 720
                                    val note = if (i == 0) "🎬 ${tier}p HD (Original MP4)" else "🎬 ${tier}p (MP4)"
                                    if (vUrl.isNotEmpty()) {
                                        formats.add(VideoFormat(vUrl, note, "mp4", VideoResolver.probeContentLength(vUrl)))
                                    }
                                }
                            } else {
                                val singleUrl = media.optString("video_url")
                                if (singleUrl.isNotEmpty()) {
                                    formats.add(VideoFormat(singleUrl, "🎬 HD Video (MP4)", "mp4", VideoResolver.probeContentLength(singleUrl)))
                                }
                            }

                            if (formats.isNotEmpty()) {
                                formats.add(VideoFormat(formats.first().formatId, "🎵 High Quality Audio (MP3)", "mp3"))
                                return ResolveOutcome.Success(caption.take(80), thumb, formats)
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return ResolveOutcome.Failed("Instagram direct extraction failed")
    }
}

/** Resolves Twitter / X videos directly using Twitter Syndication JSON API, FxTwitter, VxTwitter, and TwitSave fallback. */
private class TwitterDirectResolver : Resolver {
    override val name = "twitter-direct"
    private val client = NetworkClient.client

    private fun buildNoteForVariant(vUrl: String, defaultRank: Int): Pair<Int, String> {
        val dimMatch = Regex("/(\\d+)x(\\d+)/").find(vUrl)
        val w = dimMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val h = dimMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val tier = if (w > 0 && h > 0) minOf(w, h) else when (defaultRank) {
            0 -> 1080
            1 -> 720
            2 -> 480
            else -> 360
        }
        val dimText = if (w > 0 && h > 0) "${w}x${h} " else ""
        val note = when {
            tier >= 1080 -> "🎬 1080p FHD (${dimText}MP4)"
            tier >= 720 -> "🎬 720p HD (${dimText}MP4)"
            tier >= 480 -> "🎬 480p SD (${dimText}MP4)"
            else -> "🎬 ${tier}p (${dimText}MP4)"
        }
        return tier to note
    }

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "twitter") return ResolveOutcome.NotApplicable

        val tweetId = Regex("/status(?:es)?/(\\d+)").find(url)?.groupValues?.get(1)

        // 1. Official Twitter Syndication JSON API (cdn.syndication.twimg.com)
        if (!tweetId.isNullOrEmpty()) {
            try {
                val synReq = Request.Builder()
                    .url("https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&token=0")
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(synReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val data = JSONObject(resp.body?.string() ?: "")
                        val rawText = data.optString("text", "").substringBefore("https://t.co/").trim()
                        val title = rawText.ifEmpty { "Twitter / X Video" }.take(90)
                        var thumb = ""
                        val mediaDetails = data.optJSONArray("mediaDetails") ?: JSONArray()
                        val mp4Variants = mutableListOf<Pair<Int, String>>()

                        for (i in 0 until mediaDetails.length()) {
                            val m = mediaDetails.getJSONObject(i)
                            if (thumb.isEmpty()) thumb = m.optString("media_url_https", "")
                            val variants = m.optJSONObject("video_info")?.optJSONArray("variants") ?: continue
                            for (j in 0 until variants.length()) {
                                val v = variants.getJSONObject(j)
                                val cType = v.optString("content_type", "")
                                val vUrl = v.optString("url", "")
                                if (vUrl.isNotEmpty() && (cType.contains("mp4") || vUrl.contains(".mp4"))) {
                                    val bitrate = v.optInt("bitrate", 0)
                                    mp4Variants.add(bitrate to vUrl)
                                }
                            }
                        }

                        if (mp4Variants.isNotEmpty()) {
                            val sorted = mp4Variants.sortedByDescending { it.first }.distinctBy { it.second }
                            val formats = mutableListOf<VideoFormat>()
                            sorted.forEachIndexed { idx, (_, vUrl) ->
                                val (_, note) = buildNoteForVariant(vUrl, idx)
                                formats.add(VideoFormat(vUrl, note, "mp4", VideoResolver.probeContentLength(vUrl)))
                            }
                            formats.add(VideoFormat(sorted.first().second, "🎵 High Quality Audio (MP3)", "mp3"))
                            return ResolveOutcome.Success(title, thumb, formats)
                        }
                    }
                }
            } catch (e: Exception) {}

            // 2. FxTwitter JSON API (api.fxtwitter.com)
            try {
                val fxReq = Request.Builder()
                    .url("https://api.fxtwitter.com/i/status/$tweetId")
                    .header("User-Agent", "HKDownloaderPro/2.0")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(fxReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val data = JSONObject(resp.body?.string() ?: "")
                        val tweet = data.optJSONObject("tweet")
                        if (tweet != null) {
                            val title = tweet.optString("text", "").ifEmpty { "Twitter / X Video" }.take(90)
                            val videos = tweet.optJSONObject("media")?.optJSONArray("videos") ?: JSONArray()
                            var thumb = ""
                            val mp4Variants = mutableListOf<Pair<Int, String>>()

                            for (i in 0 until videos.length()) {
                                val vid = videos.getJSONObject(i)
                                if (thumb.isEmpty()) thumb = vid.optString("thumbnail_url", "")
                                val variants = vid.optJSONArray("variants") ?: JSONArray()
                                for (j in 0 until variants.length()) {
                                    val v = variants.getJSONObject(j)
                                    val vUrl = v.optString("url", "")
                                    val cType = v.optString("content_type", "")
                                    if (vUrl.isNotEmpty() && (cType.contains("mp4") || vUrl.contains(".mp4"))) {
                                        mp4Variants.add(v.optInt("bitrate", 0) to vUrl)
                                    }
                                }
                                if (mp4Variants.isEmpty()) {
                                    val directU = vid.optString("url", "")
                                    if (directU.isNotEmpty()) mp4Variants.add(2_000_000 to directU)
                                }
                            }

                            if (mp4Variants.isNotEmpty()) {
                                val sorted = mp4Variants.sortedByDescending { it.first }.distinctBy { it.second }
                                val formats = mutableListOf<VideoFormat>()
                                sorted.forEachIndexed { idx, (_, vUrl) ->
                                    val (_, note) = buildNoteForVariant(vUrl, idx)
                                    formats.add(VideoFormat(vUrl, note, "mp4", VideoResolver.probeContentLength(vUrl)))
                                }
                                formats.add(VideoFormat(sorted.first().second, "🎵 High Quality Audio (MP3)", "mp3"))
                                return ResolveOutcome.Success(title, thumb, formats)
                            }
                        }
                    }
                }
            } catch (e: Exception) {}

            // 3. VxTwitter JSON API (api.vxtwitter.com)
            try {
                val vxReq = Request.Builder()
                    .url("https://api.vxtwitter.com/i/status/$tweetId")
                    .header("User-Agent", "HKDownloaderPro/2.0")
                    .header("Accept", "application/json")
                    .build()
                client.newCall(vxReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val data = JSONObject(resp.body?.string() ?: "")
                        val title = data.optString("text", "").ifEmpty { "Twitter / X Video" }.take(90)
                        val mediaExt = data.optJSONArray("media_extended") ?: JSONArray()
                        var thumb = ""
                        val formats = mutableListOf<VideoFormat>()
                        for (i in 0 until mediaExt.length()) {
                            val m = mediaExt.getJSONObject(i)
                            if (thumb.isEmpty()) thumb = m.optString("thumbnail_url", "")
                            val vUrl = m.optString("url", "")
                            if (vUrl.isNotEmpty() && (m.optString("type") == "video" || m.optString("type") == "gif" || vUrl.contains(".mp4"))) {
                                val (_, note) = buildNoteForVariant(vUrl, i)
                                formats.add(VideoFormat(vUrl, note, "mp4", VideoResolver.probeContentLength(vUrl)))
                            }
                        }
                        if (formats.isNotEmpty()) {
                            formats.add(VideoFormat(formats.first().formatId, "🎵 High Quality Audio (MP3)", "mp3"))
                            return ResolveOutcome.Success(title, thumb, formats)
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 4. TwitSave HTML fallback
        return try {
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://twitsave.com/info?url=$encodedUrl")
                .header("User-Agent", NetworkClient.USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolveOutcome.Failed("HTTP ${response.code} from TwitSave")
                val html = response.body?.string() ?: return ResolveOutcome.Failed("Empty response from TwitSave")

                val titleMatch = Regex("<div[^>]*class=\"[^\"]*leading-tight[^\"]*\"[^>]*>.*?<p[^>]*class=\"[^\"]*m-2[^\"]*\"[^>]*>([^<]+)</p>", RegexOption.DOT_MATCHES_ALL).find(html)
                    ?: Regex("<div[^>]*class=\"[^\"]*leading-tight[^\"]*\"[^>]*>.*?<p[^>]*>([^<]+)</p>", RegexOption.DOT_MATCHES_ALL).find(html)
                var title = titleMatch?.groupValues?.get(1)?.trim() ?: "Twitter / X Video"
                title = title.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

                val thumbMatch = Regex("(https://pbs\\.twimg\\.com/[^\\s\"'<>]+\\.(?:jpg|jpeg|png|webp))").find(html)
                val thumbnail = thumbMatch?.groupValues?.get(1) ?: ""

                val linkMatches = Regex("href=\"https://twitsave\\.com/download\\?file=([^\"&]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(html)
                val formats = mutableListOf<VideoFormat>()
                val seenUrls = mutableSetOf<String>()

                for (match in linkMatches) {
                    val b64Token = match.groupValues[1]
                    val linkBody = match.groupValues[2]
                    try {
                        val unquoted = java.net.URLDecoder.decode(b64Token, "UTF-8")
                        val padLen = (4 - unquoted.length % 4) % 4
                        val padded = unquoted + "=".repeat(padLen)
                        val directUrl = String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()

                        if (!directUrl.startsWith("http") || directUrl in seenUrls) continue
                        seenUrls.add(directUrl)

                        val resMatch = Regex("Resolution:\\s*(\\d+)x(\\d+)").find(linkBody)
                        val w = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1280
                        val h = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 720
                        val tier = minOf(w, h)

                        val note = when {
                            tier >= 1080 -> "🎬 1080p FHD (${w}x${h} MP4)"
                            tier >= 720 -> "🎬 720p HD (${w}x${h} MP4)"
                            tier >= 480 -> "🎬 480p SD (${w}x${h} MP4)"
                            else -> "🎬 ${tier}p (${w}x${h} MP4)"
                        }

                        val size = VideoResolver.probeContentLength(directUrl)
                        formats.add(VideoFormat(directUrl, note, "mp4", size))
                    } catch (e: Exception) {}
                }

                if (formats.isNotEmpty()) {
                    val bestStream = formats.first().formatId
                    formats.add(VideoFormat(bestStream, "🎵 High Quality Audio (MP3)", "mp3"))
                    ResolveOutcome.Success(title, thumbnail, formats)
                } else {
                    ResolveOutcome.Failed("No video streams found in tweet")
                }
            }
        } catch (e: Exception) {
            ResolveOutcome.Failed(e.message ?: "network error")
        }
    }
}


data class VideoFormat(
    val formatId: String,
    val note: String,
    val ext: String,
    val sizeBytes: Long? = null
)
