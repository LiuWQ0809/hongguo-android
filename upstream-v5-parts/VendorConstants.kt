// SPDX-License-Identifier: GPL-3.0-only
// Playback signing adapted from N3urda/hongguoTV 6672b55 (2026-11-16).
// Derived from drpys 22261ad; GPL-3.0. See kotlin-tv/NOTICE.md.
package com.hongguo.api.playback

internal object VendorConstants {
    const val VIDEO_UA =
        "com.phoenix.read/71332 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)"
    const val APP_UA =
        "com.phoenix.read/72232 (Linux; U; Android 16; zh_CN; 25053RT47C; Build/BP2A.250605.031.A3)"

    /** 播放模型请求使用的厂商 UA；旧 `/novel/player` 与新 `video_tab` 接口一致。 */
    const val PLAYER_UA =
        "com.phoenix.read/71532 (Linux; U; Android 9; SM-N9860; Build/PQ3A.190705.10241111;tt-ok/3.12.13.20)"
    val device =
        linkedMapOf(
            "iid" to "1905892595382586",
            "device_id" to "1905892595378490",
            "ac" to "wifi",
            "channel" to "update_64",
            "aid" to "8662",
            "app_name" to "novelread",
            "version_code" to "71332",
            "version_name" to "7.1.3.32",
            "device_platform" to "android",
            "os" to "android",
            "ssmix" to "a",
            "device_type" to "25053RT47C",
            "device_brand" to "Redmi",
            "language" to "zh",
            "os_api" to "36",
            "os_version" to "16",
            "manifest_version_code" to "71332",
            "resolution" to "1280*2772",
            "dpi" to "520",
            "update_version_code" to "71332",
            "host_abi" to "arm64-v8a",
            "dragon_device_type" to "phone",
            "pv_player" to "71332",
            "compliance_status" to "0",
            "need_personal_recommend" to "1",
            "player_so_load" to "1",
            "is_android_pad_screen" to "0",
        )
    val sbox =
