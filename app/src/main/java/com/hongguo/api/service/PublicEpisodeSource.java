package com.hongguo.api.service;

import com.hongguo.api.util.Http;
import com.hongguo.api.util.Json;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 以 JSON 取得单集的公开媒体地址，交给原生播放器。 */
final class PublicEpisodeSource {
    private static final String ENDPOINT = "https://hongguoduanju.com/api/video/protected/fallback-play-info";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private PublicEpisodeSource() {}

    static Stream.VideoTrack fetch(String episodeId) throws Exception {
        byte[] body = Json.stringify(Map.of("item_id", episodeId)).getBytes(StandardCharsets.UTF_8);
        Http.Req request = new Http.Req(ENDPOINT).method("POST").timeout(6000).body(body)
                .header("Accept", "application/json").header("Content-Type", "application/json");
        request.followRedirect = false;
        Http.Resp response = Http.sendPlaybackMetadata(request, Long.MAX_VALUE);
        AppSource.checkPlaybackResponse(response);
        if (response.status != 200 || response.body.length == 0 || response.body.length > MAX_RESPONSE_BYTES)
            throw new IOException("公开片源接口未返回有效数据");
        return parse(response.text(), System.currentTimeMillis() / 1000);
    }

    static Stream.VideoTrack parse(String text, long nowSeconds) throws Exception {
        Map<String, Object> reply = Json.parseObject(text);
        Map<String, Object> base = Json.optObj(reply.get("base_resp"));
        Object code = base == null ? reply.get("code") : base.get("StatusCode");
        if (Json.optInt(code, -1) != 0) throw new IOException("公开片源不可用");
        Object expires = reply.get("url_expire");
        if (expires != null && Json.optLong(expires, 0) <= nowSeconds + 5)
            throw new IOException("公开片源已过期");
        String url = AppSource.checkedUrl(Json.optStr(reply.get("main_url"), ""), AppSource.VOD_DOMAINS);
        if (URI.create(url).getFragment() != null) throw new IOException("公开片源地址无效");
        Stream.VideoTrack track = new Stream.VideoTrack();
        track.url = url;
        track.definition = "360p";
        return track;
    }
}
