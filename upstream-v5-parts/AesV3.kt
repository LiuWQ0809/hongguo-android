// SPDX-License-Identifier: GPL-3.0-only
// Playback signing adapted from N3urda/hongguoTV 6672b55 (2026-11-16).
// Derived from drpys 22261ad; GPL-3.0-only. Protocol-specific transform, not standard AES.
package com.hongguo.api.playback

internal class AesV3(key: ByteArray, timestamp: Int, private val v5: Boolean = false) {
    private val word = timestamp and 3
    private val box = (if (v5) V5Constants.sbox else VendorConstants.sbox).copyOfRange(word * 256, (word + 1) * 256).map { it.u() }
    private val con =
        if (v5) arrayOf(
            intArrayOf(2, 0, 3, 1), intArrayOf(1, 3, 2, 0),
            intArrayOf(3, 2, 1, 0), intArrayOf(2, 3, 1, 0),
        )[word] else arrayOf(
            intArrayOf(1, 0, 2, 3),
            intArrayOf(2, 0, 3, 1),
            intArrayOf(0, 1, 3, 2),
            intArrayOf(1, 0, 2, 3),
        )[word]
    private val order =
        if (v5) arrayOf(
            intArrayOf(0,9,14,15,4,13,2,7,8,1,6,3,12,5,10,11),
            intArrayOf(0,9,14,11,4,13,2,3,8,1,6,15,12,5,10,7),
            intArrayOf(0,9,14,3,4,13,2,15,8,1,6,11,12,5,10,7),
            intArrayOf(0,9,14,15,4,13,2,3,8,1,6,11,12,5,10,7),
        )[word] else arrayOf(
            intArrayOf(0, 9, 14, 11, 4, 13, 2, 7, 8, 1, 6, 15, 12, 5, 10, 3),
            intArrayOf(0, 9, 14, 15, 4, 13, 2, 7, 8, 1, 6, 3, 12, 5, 10, 11),
            intArrayOf(0, 9, 14, 7, 4, 13, 2, 11, 8, 1, 6, 3, 12, 5, 10, 15),
            intArrayOf(0, 9, 14, 11, 4, 13, 2, 7, 8, 1, 6, 15, 12, 5, 10, 3),
        )[word]
    private val keys: Array<IntArray>

    init {
        val initial =
            if (v5) intArrayOf(0xbbb5de72.toInt(), 0xd4638d5b.toInt(),
                0x92a5f73b.toInt(), 0x89f8a1c9.toInt())[word] else intArrayOf(
                0xca025ddc.toInt(),
                0x823dc546.toInt(),
                0xc9420583.toInt(),
                0xc298225f.toInt(),
            )[word]
        val expanded =
            (xor(le32(initial) + le32(initial) + le32(initial) + le32(initial), key) +
                    ByteArray(32))
                .map { it.u() }
