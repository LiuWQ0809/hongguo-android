package com.hongguo.api.service;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Repr-Digest describes the entire representation, including for partial responses. */
final class MediaRepresentation {
    private static final Pattern DIGEST = Pattern.compile("(sha-256|sha-512)=:([A-Za-z0-9+/]+={0,2}):");
    private MediaRepresentation() {}
    static Map<String, String> parse(String header) {
        if (header == null || header.length() > 4096) return Collections.emptyMap();
        Map<String, String> result = new LinkedHashMap<>();
        for (String member : header.split(",")) {
            Matcher match = DIGEST.matcher(member.trim());
            if (!match.matches()) continue;
            String algorithm = match.group(1), value = match.group(2);
            try {
                byte[] decoded = Base64.getDecoder().decode(value);
                int size = "sha-256".equals(algorithm) ? 32 : 64;
                if (decoded.length != size || result.containsKey(algorithm)) return Collections.emptyMap();
                result.put(algorithm, Base64.getEncoder().encodeToString(decoded));
            } catch (IllegalArgumentException invalid) { return Collections.emptyMap(); }
        }
        return result;
    }
    static boolean same(Map<String, String> original, Map<String, String> candidate) {
        boolean matched = false;
        for (Map.Entry<String, String> entry : original.entrySet()) {
            String value = candidate.get(entry.getKey());
            if (value == null) continue;
            if (!value.equals(entry.getValue())) return false;
            matched = true;
        }
        return matched;
    }
}
