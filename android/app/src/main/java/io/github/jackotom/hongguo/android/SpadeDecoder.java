package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Registered-model media decoding: spade_a content keys and base64 media URLs. Mirrors src/native/app-source.js. */
final class SpadeDecoder {
    static final class DecodeException extends IOException {
        DecodeException(String message) { super(message); }
    }
    private SpadeDecoder() { }
    private static final byte[] URL_CONSTANTS = Base64.getDecoder().decode(
        "TdTC5rgxYgkOUrPHpnM7pByyRiuCmrWKGWs521cXdST0m69/COjWjSanLjfBqVovHwWlGJKu8pSXMrYqOKrdWA==");

    /** Some VOD models wrap their media URLs with their own key_seed. */
    static String modelMediaUrl(String encoded, String seed) throws IOException {
        try { return registeredMediaUrl(encoded); }
        catch (DecodeException invalid) {
            if (seed.isEmpty() || seed.length() > 1024 || encoded.length() > 32768) throw invalid;
        }
        byte[] raw = base64(encoded.trim().replace('-', '+').replace('_', '/'));
        int length = ((raw.length - 4) / 16) * 16;
        if (length < 16 || (raw[0] & 255) != 0xa8 || raw[2] != 1 || raw[3] != 0)
            throw new DecodeException("备用片源地址编码无效");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-512");
            hash.update(hash.digest(base64(seed.trim().replace('-', '+').replace('_', '/'))));
            byte[] key = hash.digest(URL_CONSTANTS);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, 0, 16, "AES"), new IvParameterSpec(key, 16, 16));
            byte[] plain = cipher.doFinal(raw, 4, length);
            int end = plain.length;
            int padding = plain[end - 1] & 255;
            if (padding >= 1 && padding <= 16) {
                int cursor = end - padding;
                while (cursor < end && (plain[cursor] & 255) == padding) cursor++;
                if (cursor == end) end -= padding;
            }
            while (end > 0 && plain[end - 1] == 0) end--;
            return registeredMediaUrl(new String(plain, 0, end, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException invalid) { throw new DecodeException("备用片源地址编码无效"); }
    }

    private static byte[] base64(String text) throws DecodeException {
        StringBuilder padded = new StringBuilder(text);
        while (padded.length() % 4 != 0) padded.append('=');
        try { return Base64.getDecoder().decode(padded.toString()); }
        catch (IllegalArgumentException invalid) { throw new DecodeException("App 单集片源编码无效"); }
    }
    static byte[] registeredContentKey(String value) throws IOException {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > 2048 || !text.matches("[A-Za-z0-9+/_-]+={0,2}")) throw new DecodeException("spade_a encoding invalid");
        byte[] raw;
        try { raw = base64(text.replace('-', '+').replace('_', '/')); }
        catch (DecodeException invalid) { throw new DecodeException("spade_a encoding invalid"); }
        if (raw.length < 3 || raw.length > 1024) throw new DecodeException("spade_a length invalid");
        int tagLength = ((raw[0] ^ raw[1] ^ raw[2]) & 255) - 48;
        int length = raw.length - tagLength - 1;
        if (tagLength < 1 || length < 33 || length >= raw.length) throw new DecodeException("spade_a structure invalid");
        int seed = raw[raw.length - tagLength - 2] ^ raw[raw.length - tagLength - 1];
        StringBuilder tag = new StringBuilder();
        for (int i = raw.length - tagLength; i < raw.length; i++) tag.append((char) ((raw[i] ^ seed) & 255));
        String tagText = tag.toString();
        if (tagText.equals("app_v2") || tagText.equals("web_v2")) throw new DecodeException("spade_a format unsupported");
        // decoded[i] = ((previous[i&1] ^ raw[i+1]) - 21 - bitcount(i)) & 255; key = decoded[1..32].
        byte[] decoded = new byte[length];
        int[] previous = { 250, 85 };
        for (int i = 0; i < length; i++) {
            int current = raw[i + 1] & 255;
            decoded[i] = (byte) (((previous[i & 1] ^ current) - 21 - Integer.bitCount(i)) & 255);
            previous[i & 1] = current;
        }
        byte[] keyBytes = Arrays.copyOfRange(decoded, 1, 33);
        for (byte b : keyBytes) if ((b & 255) > 127) throw new DecodeException("App 视频密钥格式无效");
        String hex = new String(keyBytes, StandardCharsets.US_ASCII);
        if (!hex.matches("(?i)[0-9a-f]{32}")) throw new DecodeException("App 视频密钥格式无效");
        byte[] key = new byte[16];
        for (int i = 0; i < 16; i++) key[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return key;
    }
    static String registeredMediaUrl(String value) throws IOException {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("https://")) return text;
        if (text.isEmpty() || text.length() > 32768 || !text.matches("[A-Za-z0-9+/]+={0,2}") || text.length() % 4 == 1)
            throw new DecodeException("App 单集片源编码无效");
        String decoded = new String(base64(text), StandardCharsets.UTF_8);
        if (!decoded.startsWith("https://")) throw new DecodeException("App 单集片源地址无效");
        return decoded;
    }
}
