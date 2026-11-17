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
                .toIntArray()
        var rounds = 8
        for (i in 4 until 12) {
            val at = 4 * (i - 1)
            var a = expanded[at]
            var b = expanded[at + 1]
            var c = expanded[at + 2]
            var d = expanded[at + 3]
            if (i and 3 == 0) {
                val t = ((initial shr (rounds and 24)) xor box[b]) and 255
                b = box[c]
                c = box[d]
                d = box[a]
                a = t
            }
            rounds += 2
            expanded[at + 4] = a xor expanded[at - 12]
            expanded[at + 5] = b xor expanded[at - 11]
            expanded[at + 6] = c xor expanded[at - 10]
            expanded[at + 7] = d xor expanded[at - 9]
        }
        keys = Array(12) { expanded.copyOfRange(it * 4, it * 4 + 4) }
    }

    private fun block(value: ByteArray): ByteArray {
        val rows = Array(4) { i -> IntArray(4) { j -> value[i * 4 + j].u() } }
        fun addCon(offset: Int) {
            for (i in 0..3) for (j in 0..3) rows[i][j] = rows[i][j] xor keys[offset + i][con[j]]
        }
        addCon(0)
        for (round in 1..2) {
            for (i in 0..3) for (j in 0..3) rows[i][j] = box[rows[i][j]]
            val before = rows.map { it.copyOf() }
            for (i in 0..3) rows[i] = before[con[i]].copyOf()
            val flat = rows.flatMap { it.toList() }
            for (i in 0..15) rows[i / 4][i % 4] = flat[order[i]]
            if (round == 1) {
                for (i in 0..3) {
                    val old = rows[i].copyOf()
                    rows[i] = IntArray(4) { old[con[it]] }
                }
                fun xt(x: Int) = ((x shl 1) xor (if (x and 128 != 0) 0x1b else 0)) and 255
                for (i in 0..3) {
                    val t = rows[0][i] xor rows[1][i] xor rows[2][i] xor rows[3][i]
                    val u = rows[0][i]
