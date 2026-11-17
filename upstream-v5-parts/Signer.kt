// SPDX-License-Identifier: GPL-3.0-only
// Playback signing adapted from N3urda/hongguoTV 6672b55 (2026-11-16).
// Kotlin port of drpys 22261adfa31435e3b3ef5a730b8a2a75bcaf1715; GPL-3.0-only.
// Only hash branch 0 is emitted: adjust the request ticket before signing.
package com.hongguo.api.playback

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class Signer(
    private val clock: () -> Long = System::currentTimeMillis,
    private val device: Map<String, String> = VendorConstants.device,
    private val random: () -> Double = Math::random,
    // 官方 App 的 medusa 明文内嵌「进程 nonce」（每次冷启动随机生成一次）与
    // 「进程启动时间」；测试对拍时注入固定值与 oracle 对齐。
    private val processNonce: String = java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(ByteArray(18).also(java.security.SecureRandom()::nextBytes)),
    private val mobileV5: Boolean = false,
) {
    private val processStarted: Long = clock()
    data class Request(val url: String, val headers: Map<String, String>, val body: ByteArray?)

    private fun rand() = (random() * 4294967296.0).toLong().toInt()

    private fun unsigned(v: Int) = v.toLong() and 0xffffffffL

    private fun varint(value: Long): ByteArray {
        var n = value
        return ByteArrayOutputStream()
            .apply {
                while (n > 127) {
                    write(((n and 127) or 128).toInt())
                    n = n ushr 7
                }
                write(n.toInt())
            }
            .toByteArray()
    }

    private data class Field(val tag: Int, val value: Any, val type: String = "string")

    private fun f(tag: Int, value: Any, type: String = "string") = Field(tag, value, type)
