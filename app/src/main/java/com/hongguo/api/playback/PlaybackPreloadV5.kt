// SPDX-License-Identifier: GPL-3.0-only
// Native preload request contract from tsukiyo-zero/hongguoTV kotlin-v0.1.38.
package com.hongguo.api.playback

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

internal object PlaybackPreloadV5 {
    private const val endpoint = "https://reading.snssdk.com/novel/player/multi_video_model/preload/v1"
    private val signer = Signer(mobileV5 = true, playbackStatus = 312)

    @JvmStatic
    fun request(id: String, deviceId: String, iid: String, cdid: String, now: Long): MobilePlayerV5.Request {
        require(listOf(id, deviceId, iid).all { it.matches(Regex("[0-9]{1,30}")) })
        val query = linkedMapOf(
            "aid" to "8662", "app_name" to "novelread", "version_code" to "74532",
            "version_name" to "7.4.5.32", "update_version_code" to "74532", "manifest_version_code" to "74532",
            "os" to "android", "ssmix" to "a", "channel" to "update_64", "device_platform" to "android",
            "device_type" to "25053RT47C", "device_brand" to "Redmi", "os_version" to "16", "os_api" to "36",
            "ac" to "wifi", "language" to "zh", "host_abi" to "arm64-v8a",
            "iid" to iid, "cdid" to cdid, "device_id" to deviceId,
        )
        val biz = JSONObject().put("detail_page_version", 0).put("device_level", 2)
            .put("disable_digg_stat", false).put("disable_video_relate_book", false)
            .put("need_all_video_definition", true).put("need_mp4_align", false)
            .put("use_os_player", false).put("use_server_dns", false).put("video_platform", 1024)
        val payload = JSONObject().put("biz_param", biz)
            .put("mixed_video_id_map", JSONObject().put("1004", org.json.JSONArray().put(id)))
            .put("dr_scene", "preload")
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val body = output.toByteArray()
        val signed = signer.signPlayback(endpoint, query, body, now)
        val headers = signed.headers + mapOf(
            "User-Agent" to System.getProperty("http.agent", "okhttp/4.12.0"),
            "Accept" to "application/json", "Content-Encoding" to "gzip",
        )
        return MobilePlayerV5.Request(signed.url, headers, body)
    }
}
