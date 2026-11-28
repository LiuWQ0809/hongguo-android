package io.github.jackotom.hongguo.android;

import org.json.JSONException;
import org.json.JSONObject;

/** Compact continuation for the same official genre and feed session. */
record RecommendationCursor(String genre, int offset, String sessionId) {
    static final String EXTRA = "recommendationCursor";

    static RecommendationCursor parse(Object raw) throws JSONException {
        if (!(raw instanceof JSONObject value) || !(value.opt("genre") instanceof String genre)
                || !genre.matches("[a-z_]{1,30}") || !(value.opt("offset") instanceof Number offset)
                || offset.doubleValue() != offset.intValue() || offset.intValue() <= 0
                || !(value.opt("sessionId") instanceof String session) || session.length() > 128
                || session.codePoints().anyMatch(Character::isISOControl))
            throw new JSONException("推荐分页信息无效");
        return new RecommendationCursor(genre, offset.intValue(), session);
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("genre", genre).put("offset", offset).put("sessionId", sessionId);
    }

    RecommendationCursor after(JSONObject page) throws JSONException {
        if (!page.optBoolean("hasMore")) return null;
        int next = page.optInt("offset");
        if (next <= offset) throw new JSONException("推荐分页未前进");
        return parse(new JSONObject().put("genre", genre).put("offset", next)
            .put("sessionId", page.optString("sessionId", "").isEmpty() ? sessionId : page.getString("sessionId")));
    }

    JSONObject fetch() throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        try (OfficialApiClient client = new OfficialApiClient(OfficialApiClient.newSession())) {
            client.deadlineNanos(deadline);
            return NewReleaseCatalog.feed(client, offset, sessionId, genre);
        }
    }
}
