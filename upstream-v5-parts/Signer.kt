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

    private fun proto(vararg fields: Field): ByteArray =
        ByteArrayOutputStream()
            .apply {
                for ((tag, value, type) in fields) {
                    when (type) {
                        "sint" -> {
                            val n = (value as Number).toLong()
                            write(varint(tag.toLong() shl 3))
                            write(varint(if (n < 0) -n * 2 - 1 else n * 2))
                        }
                        "float" -> {
                            write(varint((tag.toLong() shl 3) or 5))
                            write(
                                ByteBuffer.allocate(4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .putFloat((value as Number).toFloat())
                                    .array()
                            )
                        }
                        else -> {
                            val bytes =
                                if (value is ByteArray) value else value.toString().toByteArray()
                            if (bytes.isNotEmpty()) {
                                write(varint((tag.toLong() shl 3) or 2))
                                write(varint(bytes.size.toLong()))
                                write(bytes)
                            }
                        }
                    }
                }
            }
            .toByteArray()

    private fun getIv(initial: Int, data: ByteArray): Int {
        var value = initial
        for (i in data.indices) value =
            if (i and 1 == 0) (value ushr 4) xor value xor (value shl 6) xor data[i].u()
            else ((value ushr 7) xor value xor (data[i].u() or (value shl 12))).inv()
        return value
    }

    private fun iv(query: ByteArray, body: ByteArray, ts: Int) =
        getIv(getIv(getIv(0x20230928, query), body), le32(ts))

    fun branch(query: String, body: ByteArray?, ts: Int): Int =
        (iv(sm3(query.toByteArray()), body?.let { digest("MD5", it) } ?: ByteArray(16), ts) and 15)
            .let { if (mobileV5) { if (it == 12) 0 else 1 } else it % 3 }

    private fun sumMd5(data: ByteArray): Int {
        var check = 0x20220420
        for (i in 0 until 12) {
            val temp = (check ushr (if (i and 1 == 0) 3 else 5)) xor check
            check =
                if (i and 1 == 0) data[i].u() xor (check shl 7)
                else (data[i].u() or (check shl 11)).inv()
            check = check xor temp
        }
        return (check or 4) xor 0x1000000
    }

    fun hashF13(query: ByteArray, body: ByteArray, ts: Int): ByteArray {
        val index = (iv(query, body, ts) and 15) / 3
        require(if (mobileV5) (iv(query, body, ts) and 15) == 12
            else (iv(query, body, ts) and 15) % 3 == 0) { "Unsupported hash branch" }
        val ivV1 =
            if (mobileV5) 0x9d4a97c2.toInt() else intArrayOf(
                0xc4a78580.toInt(),
                0xb3c0fd39.toInt(),
                0xc58c5686.toInt(),
                0xc9aa3ba7.toInt(),
                0xf5a7adf2.toInt(),
                0x963c2ed1.toInt(),
            )[index]
        val count = ivV1 + ts
        val tt = VendorConstants.hashTable.map { ror(it, (count + 1) and 255) }
        val shift = (count + 2) and 7
        val pad =
            intArrayOf(0xfa, 0x45, 0x61, 0xd7)
                .map { ((it or (it shl 8)) shr shift).toByte() }
                .toByteArray()
        val data = query + body + le32(ts) + pad + hex("00000000000001a0")
        val di = (data.indices step 4).map { data.i32(it) }.toMutableList()
        for (i in 0 until 112) {
            val x = di[i + 1]
            val y = di[i + 14]
            di.add(
                di[i] +
                    di[i + 9] +
                    (rol(x, 14) xor rol(x, 25) xor (x ushr 3)) +
