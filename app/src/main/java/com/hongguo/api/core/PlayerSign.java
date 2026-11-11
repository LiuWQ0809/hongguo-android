// SPDX-License-Identifier: GPL-3.0-only
package com.hongguo.api.core;

import com.hongguo.api.util.Crypto;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 播放签名参考 Xanerix/guoapp ad8dc125（GPL-3.0）。 */
public final class PlayerSign {
    private PlayerSign() {}

    private static byte[] utf8(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] join(byte[]... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.write(value, 0, value.length);
        return out.toByteArray();
    }
    private static byte[] varint(long value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((value & ~127L) != 0) { out.write(((int) value & 127) | 128); value >>>= 7; }
        out.write((int) value);
        return out.toByteArray();
    }
    private static byte[] number(int field, long value) { return join(varint(field * 8L), varint(value)); }
    private static byte[] field(int field, byte[] value) {
        return join(varint(field * 8L + 2), varint(value.length), value);
    }
    private static byte[] text(int field, String value) { return field(field, utf8(value)); }
    private static byte[] pad(byte[] data) {
        int n = 16 - data.length % 16;
        byte[] result = Arrays.copyOf(data, data.length + n);
        Arrays.fill(result, data.length, result.length, (byte) n);
        return result;
    }
    private static ByteBuffer little(byte[] data) { return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); }

    static String gorgon(String query, byte[] body, long timestamp) {
        byte[] data = new byte[20];
        System.arraycopy(Crypto.md5(utf8(query)), 0, data, 0, 4);
        if (body != null) System.arraycopy(Crypto.md5(body), 0, data, 4, 4);
        System.arraycopy(new byte[]{0, 6, 11, 28}, 0, data, 12, 4);
        ByteBuffer.wrap(data, 16, 4).putInt((int) timestamp);
        byte[] key = Crypto.unhex("44b9b9d9a4aef9fca493aa757ca3c2c4a496938f");
        for (int i = 0; i < data.length; i++) data[i] ^= key[i];
        for (int i = 0; i < data.length; i++) {
            int current = data[i] & 255;
            int mixed = ((current << 4) | (current >>> 4)) & 255;
            mixed ^= data[(i + 1) % data.length] & 255;
            data[i] = (byte) ((Integer.reverse(mixed) >>> 24) ^ 255 ^ data.length);
        }
        return Crypto.hex(join(Crypto.unhex("8404401c0000"), data));
    }
}
