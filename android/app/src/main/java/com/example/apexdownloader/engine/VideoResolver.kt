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
    private val cloudflarePagesResolver = CloudflarePagesResolver()
    private val desktopServerResolver = DesktopServerResolver()
    private val cobaltResolver = CobaltResolver()
    private val localDirectLinkResolver = LocalDirectLinkResolver()

    private fun chainFor(type: String): List<Resolver> {
        val base = when (type) {
            "google-drive", "dropbox" -> listOf(cloudStorageResolver)
            "tiktok" -> listOf(tikTokPublicApiResolver, cloudflarePagesResolver, desktopServerResolver, cobaltResolver)
            "direct-link" -> listOf(localDirectLinkResolver)
            in videoPlatformTypes -> listOf(cloudflarePagesResolver, desktopServerResolver, cobaltResolver)
            else -> listOf(localDirectLinkResolver)
        }
        // Adaptive reorder: whichever backend won last time for this type
        // jumps to the front, so a temporary outage of the "default first
        // choice" doesn't keep costing a full timeout on every request.
        val lastGood = lastSuccessfulResolver[type] ?: return base
        val winner = base.find { it.name == lastGood } ?: return base
        return listOf(winner) + base.filter { it.name != lastGood }
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
        for (resolver in chain) {
            when (val outcome = resolver.resolve(url, type, ctx)) {
                is ResolveOutcome.Success -> {
                    lastSuccessfulResolver[type] = resolver.name
                    onResult(outcome.title, outcome.thumbnail, outcome.formats)
                    return
                }
                is ResolveOutcome.Failed -> failures.add("${resolver.name}: ${outcome.reason}")
                ResolveOutcome.NotApplicable -> { /* skip silently, doesn't count as a failure */ }
            }
        }

        // Every applicable backend either wasn't configured or genuinely failed.
        if (type in videoPlatformTypes) {
            val detail = if (failures.isNotEmpty()) " (tried: ${failures.joinToString("; ")})" else ""
            onError(
                "Couldn't resolve this ${type.replaceFirstChar { it.uppercase() }} link. Add a Desktop Sync connection " +
                    "or a self-hosted Cobalt instance URL in Settings, then try again.$detail"
            )
        } else if (failures.isNotEmpty()) {
            onError("Couldn't resolve this link (tried: ${failures.joinToString("; ")}).")
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
        return if (id != null) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else ""
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

/** TikTok via the public tikwm.com API -- fast and dedicated to TikTok
 *  specifically, so it's tried before the more general-purpose backends. */
private class TikTokPublicApiResolver : Resolver {
    override val name = "tikwm"
    private val client = NetworkClient.client

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        if (type != "tiktok") return ResolveOutcome.NotApplicable
        return try {
            val request = Request.Builder()
                .url("https://www.tikwm.com/api/?url=${java.net.URLEncoder.encode(url, "UTF-8")}")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ResolveOutcome.Failed("HTTP ${response.code}")
                val data = JSONObject(response.body?.string() ?: "")
                if (data.optInt("code", -1) != 0) return ResolveOutcome.Failed("API returned an error")
                val v = data.optJSONObject("data") ?: return ResolveOutcome.Failed("empty response")
                val title = v.optString("title", "TikTok Video")
                val thumbnail = v.optString("cover", "")
                val formats = mutableListOf<VideoFormat>()
                v.optString("play", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "HD No Watermark (MP4)", "mp4"))
                }
                v.optString("wmplay", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "Watermarked (MP4)", "mp4"))
                }
                v.optString("music", "").takeIf { it.isNotEmpty() }?.let {
                    formats.add(VideoFormat(it, "Audio Only (MP3)", "mp3"))
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
        val instances = mutableListOf<String>()
        if (!ctx.cobaltInstanceUrl.isNullOrEmpty()) {
            instances.add(ctx.cobaltInstanceUrl)
        }

        // Fetch dynamically from cobalt.directory tracker
        try {
            val dirRequest = Request.Builder()
                .url("https://cobalt.directory/api/working?type=api")
                .build()
            client.newCall(dirRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val dirData = JSONObject(bodyStr).optJSONObject("data")
                    if (dirData != null) {
                        val service = type.lowercase()
                        val serviceArr = dirData.optJSONArray(service)
                        if (serviceArr != null) {
                            for (i in 0 until serviceArr.length()) {
                                instances.add(serviceArr.getString(i))
                            }
                        }
                        // Fallback: add all other services' APIs as well
                        val keys = dirData.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k != service) {
                                val otherArr = dirData.optJSONArray(k)
                                if (otherArr != null) {
                                    for (i in 0 until otherArr.length()) {
                                        instances.add(otherArr.getString(i))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore & fallback to static
        }

        val uniqueInstances = instances.distinct().toMutableList()
        if (uniqueInstances.isEmpty() || (uniqueInstances.size == 1 && uniqueInstances[0] == ctx.cobaltInstanceUrl)) {
            uniqueInstances.addAll(staticFallbacks)
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

/** Resolves video links through the Cloudflare Pages custom backend. */
private class CloudflarePagesResolver : Resolver {
    override val name = "cloudflare-pages"
    private val client = NetworkClient.client

    override suspend fun resolve(url: String, type: String, ctx: ResolverContext): ResolveOutcome {
        val endpoint = "https://hk-downloader-pro2.pages.dev/api/analyze"
        return try {
            val jsonReq = JSONObject().apply {
                put("inputUrl", url)
            }.toString()
            val requestBody = jsonReq.toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

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
                        val directUrl = fObj.optString("directUrl")
                        val token = fObj.optString("token")
                        // If directUrl is empty, fallback to token or construct download URL
                        val formatId = if (directUrl.isNotEmpty()) {
                            "cobalt|$directUrl"
                        } else if (token.isNotEmpty()) {
                            "cobalt|https://hk-downloader-pro2.pages.dev/api/download?token=$token"
                        } else {
                            ""
                        }
                        if (formatId.isNotEmpty()) {
                            val ext = fObj.optString("ext", "mp4")
                            val note = fObj.optString("note", "Download")
                            val sizeBytes = fObj.optLong("sizeBytes", -1L).takeIf { it > 0 }
                                ?: fObj.optLong("size", -1L).takeIf { it > 0 }
                            formatsList.add(VideoFormat(formatId, note, ext, sizeBytes = sizeBytes))
                        }
                    }
                }
                if (formatsList.isEmpty()) {
                    ResolveOutcome.Failed("no formats resolved")
                } else {
                    ResolveOutcome.Success(title, thumbnail, formatsList)
                }
            }
        } catch (e: Exception) {
            ResolveOutcome.Failed(e.message ?: "unreachable")
        }
    }
}


data class VideoFormat(
    val formatId: String,
    val note: String,
    val ext: String,
    val sizeBytes: Long? = null
)
