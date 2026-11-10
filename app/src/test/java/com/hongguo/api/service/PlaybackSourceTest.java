package com.hongguo.api.service;

import com.hongguo.api.util.Config;
import com.hongguo.api.util.Crypto;
import com.hongguo.api.util.Json;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** 2026-10-27 播放取链恢复：匿名设备注册 + 无签名播放请求 + 内联片源模型。 */
public class PlaybackSourceTest {

    /** 19 位、超出 double 精度的设备号（合成值，非真实身份）。 */
    private static final String DEVICE_ID = "7432109876543210987";
    private static final String INSTALL_ID = "7432109876543210123";

    /** Spade 自检真值之一，解出合法 content key。 */
    private static final String VALID_SPADE = Base64.getEncoder().encodeToString(Crypto.unhex(
            "93bc1df253ba1bf7618b19c448b806f64d810afc45b715fe5eb925f26cbf12f541ba098282"));

    @BeforeClass public static void initialize() {
        Config.initialize("{\"upstream\":{\"api_host\":\"example.invalid\"}}",
                new File(System.getProperty("java.io.tmpdir"), "hongguo-playback-test").getAbsolutePath(),
                "1234567890123456");
    }

    @Before public void reset() {
        PlaybackDevice.resetForTest(null);
        AppSource.setPlaybackPostForTest(null);
        AppSource.setPlaybackTabForTest((vid, identity) -> null);
        AppSource.setPlaybackFallbackForTest(null);
    }

    @After public void restore() {
        PlaybackDevice.resetForTest(null);
        AppSource.setPlaybackPostForTest(null);
        AppSource.setPlaybackTabForTest(null);
        AppSource.setPlaybackFallbackForTest(null);
    }

    @Test public void byteVc2FallsBackToSameVideoWithCompatibleCodec() throws Exception {
        String vid = "7600000000000000001";
        Map<String, Object> model = Json.obj();
        model.put("video_id", "same-video");
        model.put("video_duration", 60);
        model.put("fallback_api", "https://vod.snssdk.com/path?sig=a%2Bb&codec_type=4");
        List<Object> inline = Json.arr();
        inline.add(item("bytevc2", 720, 1280, "https://v1.qznovelvod.com/old.mp4", null));
        model.put("video_list", inline);
        List<String> urls = new ArrayList<>();
        AppSource.setPlaybackFallbackForTest(url -> {
            urls.add(url);
            return codecReply(url.endsWith("codec_type=1") ? "h265" : "bytevc2", "same-video");
        });
        assertEquals("https://v1.qznovelvod.com/h265.mp4", AppSource.parseModel(vid, response(vid, model), "auto").url);
        assertEquals(2, urls.size());
        assertEquals("https://vod.snssdk.com/path?sig=a%2Bb&codec_type=1", urls.get(1));
    }

    private static Map<String, Object> codecReply(String codec, String videoId) {
        Map<String, Object> track = item(codec, 720, 1280, "https://v1.qznovelvod.com/" + codec + ".mp4", null);
        Map<String, Object> list = Json.obj();
        list.put("video_1", track);
        Map<String, Object> data = Json.obj();
        data.put("video_id", videoId); data.put("video_duration", 60); data.put("video_list", list);
        Map<String, Object> info = Json.obj();
        info.put("data", data);
        Map<String, Object> outer = Json.obj();
        outer.put("video_info", info);
        return outer;
    }

    @Test public void signedTabUsesFinalUrlAndAnonymousCookieOnly() {
        String cookie = PlaybackDevice.cookieHeader(Arrays.asList("install_id=1; Path=/", "sessionid=private; Path=/", "ttreq=ok; Secure"));
        assertEquals("install_id=1; ttreq=ok", cookie);
        PlaybackDevice.Identity id = new PlaybackDevice.Identity(DEVICE_ID, INSTALL_ID, "cdid", cookie);
        long now = 1_793_000_000_123L;
        AppSource.SignedPlaybackRequest request = AppSource.signedTabRequest("7600000000000000001", id, now);
        assertEquals(cookie, request.headers.get("Cookie"));
        assertEquals(0, com.hongguo.api.core.PureSign.branchOf(request.url, null, now / 1000));
        assertNotNull(request.headers.get("x-medusa"));
    }

    @Test public void retriesWithFreshIdentityWhenFirstPlaybackRoutesFail() throws Exception {
        String vid = "7600000000000000001";
        AtomicInteger registrations = new AtomicInteger();
        PlaybackDevice.resetForTest((url, headers, body) -> { registrations.incrementAndGet(); return okReply(); });
        List<PlaybackDevice.Identity> attempts = new ArrayList<>();
        AppSource.setPlaybackPostForTest((body, identity, host) -> { throw new IOException("temporary"); });
        Map<String, Object> model = Json.obj();
        List<Object> list = Json.arr();
        list.add(item("h264", 720, 1280, "https://v1.qznovelvod.com/test.mp4", VALID_SPADE));
        model.put("video_list", list);
        AppSource.setPlaybackTabForTest((episode, identity) -> {
            attempts.add(identity);
            return attempts.size() == 1 ? null : response(episode, model);
        });
        assertEquals("https://v1.qznovelvod.com/test.mp4", AppSource.fetch(vid, "auto").url);
        assertEquals(2, registrations.get());
        assertNotSame(attempts.get(0), attempts.get(1));
    }

    @Test public void byteVc2RejectsFallbackFromDifferentVideo() throws Exception {
        String vid = "7600000000000000001";
        Map<String, Object> model = Json.obj();
        model.put("video_id", "original");
        model.put("fallback_api", "https://vod.snssdk.com/path?codec_type=4");
        List<Object> inline = Json.arr();
        inline.add(item("bytevc2", 720, 1280, "https://v1.qznovelvod.com/old.mp4", null));
        model.put("video_list", inline);
        AppSource.setPlaybackFallbackForTest(url -> codecReply("h265", "different-video"));
        try { AppSource.parseModel(vid, response(vid, model), "auto"); fail("不得播放其他分集"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("编号")); }
    }

    @Test public void compatibleCodecTimeoutStillTriesNextCodec() throws Exception {
        String vid = "7600000000000000001";
        Map<String, Object> model = Json.obj();
        model.put("fallback_api", "https://vod.snssdk.com/path?codec_type=4");
        AppSource.setPlaybackFallbackForTest(url -> {
            if (url.endsWith("codec_type=1")) throw new java.net.SocketTimeoutException("timeout");
            return codecReply(url.contains("force_codec=h264") ? "h264" : "bytevc2", "same-video");
        });
        assertEquals("https://v1.qznovelvod.com/h264.mp4", AppSource.parseModel(vid, response(vid, model), "auto").url);
    }

    // ==================== ttEncrypt 注册请求体 ====================

    @Test public void registrationBodyDecryptsToGzipPayloadWithDigest() throws Exception {
        byte[] random = new byte[32];
        for (int i = 0; i < random.length; i++) random[i] = (byte) i;
        long now = 1_793_000_000_000L;
        byte[] body = PlaybackDevice.buildBody(now, "cdid-fixed", random);

        assertArrayEquals(Crypto.unhex("746305100000"), Arrays.copyOfRange(body, 0, 6));
        assertArrayEquals(random, Arrays.copyOfRange(body, 6, 38));

        byte[] derived = PlaybackDevice.deriveKeyIv(random);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derived, 0, 16, "AES"),
                new IvParameterSpec(derived, 16, 16));
        byte[] plain = c.doFinal(body, 38, body.length - 38);
        byte[] digest = Arrays.copyOfRange(plain, 0, 64);
        byte[] compressed = Arrays.copyOfRange(plain, 64, plain.length);
        assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(compressed), digest);

        Map<String, Object> payload = Json.parseObject(new String(gunzip(compressed), StandardCharsets.UTF_8));
        assertEquals("ss_app_log", payload.get("magic_tag"));
        assertEquals(now, Json.optLong(payload.get("_gen_time"), 0));
        Map<String, Object> header = Json.optObj(payload.get("header"));
        assertEquals("cdid-fixed", header.get("cdid"));
        assertEquals(8662L, Json.optLong(header.get("aid"), 0));
        assertEquals("com.phoenix.read", header.get("package"));
        assertFalse("注册请求不得携带账号或 Cookie 字段",
                header.containsKey("cookie") || header.containsKey("user_id") || header.containsKey("sessionid"));
    }

    @Test public void registrationKeyMatchesDoubleSha512OverSalt() throws Exception {
        byte[] random = new byte[32];
        Arrays.fill(random, (byte) 7);
        MessageDigest sha = MessageDigest.getInstance("SHA-512");
        byte[] inner = sha.digest(random);
        sha.update(inner);
        sha.update(Crypto.unhex("4dd4c2e6b83162090e52b3c7a6733ba41cb2462b829ab58a196b39db57177524"
                + "f49baf7f08e8d68d26a72e37c1a95a2f1f05a51892aef2949732b62a38aadd58"));
        assertArrayEquals(Arrays.copyOf(sha.digest(), 32), PlaybackDevice.deriveKeyIv(random));
    }

    @Test public void registrationHeadersCarryStubAndNoSignature() throws Exception {
        byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
        Map<String, String> h = PlaybackDevice.headers(body, 123L);
        assertEquals(Crypto.md5HexUpper(body), h.get("x-ss-stub"));
        assertEquals("application/octet-stream;tt-data=a", h.get("Content-Type"));
        assertEquals("123", h.get("x-ss-req-ticket"));
        for (String k : h.keySet()) assertFalse(k, isSignatureHeader(k));
    }

    // ==================== 注册响应解析 ====================

    @Test public void responseIdsKeepNineteenDigitsExactly() throws Exception {
        Map<String, Object> j = Json.parseObject("{\"device_id\":" + DEVICE_ID
                + ",\"install_id\":" + INSTALL_ID + ",\"code\":0}");
        PlaybackDevice.Identity id = PlaybackDevice.parse(j, "c");
        assertEquals(DEVICE_ID, id.deviceId);
        assertEquals(INSTALL_ID, id.iid);
        assertEquals("c", id.cdid);
        assertFalse(id.toString().contains(DEVICE_ID));
    }

    @Test public void responseIdsPreferStringFields() {
        Map<String, Object> j = Json.obj();
        j.put("device_id", 1.0e18);           // 已丢精度的浮点，单独出现时必须拒绝
        j.put("device_id_str", DEVICE_ID);
        assertEquals(DEVICE_ID, PlaybackDevice.responseId(j, "device_id"));
    }

    @Test public void responseIdsRejectZeroFloatsAndJunk() {
        Object[] bad = {0L, "0", "000", 1.0e18, -5L, "-5", "12a", "", " ", "1".repeat(31), true};
        for (Object v : bad) {
            Map<String, Object> j = Json.obj();
            j.put("install_id", v);
            assertEquals(String.valueOf(v), "", PlaybackDevice.responseId(j, "install_id"));
        }
        Map<String, Object> big = Json.obj();
        big.put("install_id", new BigInteger("98765432109876543210"));
        assertEquals("98765432109876543210", PlaybackDevice.responseId(big, "install_id"));
    }

    @Test(expected = IOException.class)
    public void registrationRejectedByBusinessCode() throws Exception {
        PlaybackDevice.parse(Json.parseObject("{\"code\":1,\"device_id_str\":\"" + DEVICE_ID
                + "\",\"install_id_str\":\"" + INSTALL_ID + "\"}"), "c");
    }

    // ==================== 进程内共享与失败冷却 ====================

    @Test public void concurrentCallersShareOneRegistration() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        PlaybackDevice.resetForTest((url, headers, body) -> {
            calls.incrementAndGet();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
            assertEquals(PlaybackDevice.REGISTER_URL, url);
            return okReply();
        });
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<PlaybackDevice.Identity>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) results.add(pool.submit(PlaybackDevice::get));
            Thread.sleep(100);
            release.countDown();
            PlaybackDevice.Identity first = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<PlaybackDevice.Identity> f : results) assertSame(first, f.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, calls.get());
        assertSame(PlaybackDevice.get(), PlaybackDevice.get());
        assertEquals(1, calls.get());
    }

    @Test public void failureIsReusedOnlyDuringCooldown() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        boolean[] healthy = {false};
        PlaybackDevice.resetForTest((url, headers, body) -> {
            calls.incrementAndGet();
            if (!healthy[0]) throw new IOException("offline");
            return okReply();
        });
        IOException first = expectRegistrationFailure();
        IOException second = expectRegistrationFailure();
        assertSame("冷却期内复用同一失败，不重复注册", first, second);
        assertEquals(1, calls.get());
        assertTrue(first.getMessage().contains("匿名播放设备注册失败"));
        assertFalse("失败消息不得带出底层细节", first.getMessage().contains("offline"));

        healthy[0] = true;
        PlaybackDevice.advanceClockForTest(PlaybackDevice.FAILURE_COOLDOWN_MS + 1);
        assertEquals(DEVICE_ID, PlaybackDevice.get().deviceId);
        assertEquals(2, calls.get());
    }

    @Test public void emptyOrNonJsonRegistrationIsFailure() throws Exception {
        PlaybackDevice.resetForTest((url, headers, body) -> new PlaybackDevice.Reply(200, ""));
        expectRegistrationFailure();
        PlaybackDevice.resetForTest((url, headers, body) -> new PlaybackDevice.Reply(503, "{}"));
        expectRegistrationFailure();
    }

    // ==================== 无签名播放请求 ====================

    @Test public void playbackBaseUsesRegisteredIdentity() throws Exception {
        PlaybackDevice.resetForTest((url, headers, body) -> okReply());
        PlaybackDevice.Identity id = PlaybackDevice.get();
        String url = AppSource.playbackUrl(id, 1_793_000_000_123L);

        assertTrue(url.startsWith("https://" + AppSource.VIDEO_API_HOST + "/novel/player/multi_video_model/v1/?"));
        String query = url.substring(url.indexOf('?') + 1);
        assertTrue(query.contains("&device_id=" + DEVICE_ID + "&iid=" + INSTALL_ID + "&cdid=" + id.cdid
                + "&_rticket=1793000000123"));
        assertTrue("resolution 的 * 按 python quote 编码", query.contains("resolution=1280%2A2772"));
        assertFalse("无签名路径不带 ts", query.contains("&ts="));
        assertEquals(1, count(query, "device_id="));

        Map<String, String> headers = AppSource.playbackHeaders();
        assertEquals(AppSource.PLAYBACK_UA, headers.get("User-Agent"));
        for (String k : headers.keySet()) assertFalse(k, isSignatureHeader(k));
        assertFalse(AppSource.deviceBase().containsKey("iid"));
        assertFalse(AppSource.deviceBase().containsKey("device_id"));
    }

    @Test public void signedPlaybackUsesSameTimestampTicketAndBody() throws Exception {
        PlaybackDevice.resetForTest((url, headers, body) -> okReply());
        PlaybackDevice.Identity id = PlaybackDevice.get();
        byte[] body = "{\"example\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long now = 1_793_000_000_123L;
        AppSource.SignedPlaybackRequest request = AppSource.signedPlaybackRequest(body, id, now);
        long ticket = Long.parseLong(request.headers.get("x-ss-req-ticket"));
        long timestamp = now / 1000L;
        assertTrue(ticket >= now && ticket < now + 128);
        assertEquals(AppSource.playbackUrl(id, ticket, timestamp), request.url);
        assertEquals(0, com.hongguo.api.core.PureSign.branchOf(request.url, body, timestamp));
        assertEquals(String.valueOf(timestamp), request.headers.get("x-khronos"));
        for (String name : new String[]{"x-gorgon", "x-ladon", "x-argus", "x-helios", "x-medusa"}) {
            assertNotNull(name, request.headers.get(name));
            assertFalse(name, request.headers.get(name).isEmpty());
        }
        assertEquals("cn", request.headers.get("x-tt-store-region"));
        try {
            AppSource.signedPlaybackRequest(new byte[0], id, now);
            fail("Empty playback requests must not be signed");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }

    @Test public void tabResponseAcceptsOnlyTheRequestedEpisode() {
        String vid = "7600000000000000001";
        String body = "{\"code\":0,\"data\":{\"video_model_data\":{\"" + vid
                + "\":{\"video_model\":{\"video_list\":[{\"codec_type\":\"bytevc1\"}]}}}}}";
        assertNotNull(AppSource.parseTabResponse(vid, body));
        assertNull(AppSource.parseTabResponse("7600000000000000002", body));
        assertNull(AppSource.parseTabResponse(vid, "{\"code\":1,\"data\":{}}"));
        assertNull(AppSource.parseTabResponse(vid, ""));
    }

    @Test public void tabFallbackRunsOnlyAfterSignedPlaybackFails() throws Exception {
        String vid = "7600000000000000001";
        PlaybackDevice.resetForTest((url, headers, body) -> okReply());
        List<Object> list = Json.arr();
        list.add(item("h264", 720, 1280, "https://v1.qznovelvod.com/test.mp4", VALID_SPADE));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        AtomicInteger calls = new AtomicInteger();
        AppSource.setPlaybackTabForTest((id, identity) -> { calls.incrementAndGet(); return response(id, model); });
        AppSource.setPlaybackPostForTest((body, identity, host) -> response(vid, model));
        assertEquals("https://v1.qznovelvod.com/test.mp4", AppSource.fetch(vid, "auto").url);
        assertEquals(0, calls.get());
        AppSource.setPlaybackPostForTest((body, identity, host) -> { throw new IOException("unavailable"); });
        assertEquals("https://v1.qznovelvod.com/test.mp4", AppSource.fetch(vid, "auto").url);
        assertEquals(1, calls.get());
    }

    @Test public void emptyPlaybackResponseIsReportedAsIdentityRejection() {
        try {
            AppSource.parsePlaybackResponse("  ");
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("空响应"));
        }
        try {
            AppSource.parsePlaybackResponse("{\"code\":0}");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("App 接口未返回播放数据", e.getMessage());
        }
    }

    @Test public void emptyPlaybackResponseRetriesOnceWithRenewedIdentity() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        PlaybackDevice.resetForTest((url, headers, body) -> registrations.incrementAndGet() == 1
                ? okReply() : reply("7432109876543219999", "7432109876543218888"));
        List<PlaybackDevice.Identity> used = new ArrayList<>();
        List<String> hosts = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        AppSource.setPlaybackPostForTest((body, identity, host) -> {
            used.add(identity);
            hosts.add(host);
            bodies.add(new String(body, StandardCharsets.UTF_8));
            if (used.size() == 1) throw new AppSource.EmptyPlaybackResponseException();
            throw new IllegalStateException("reached retry");
        });
        try {
            AppSource.fetch("7000000000000000001", "auto");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("reached retry", e.getMessage());
        }
        assertEquals(3, used.size());
        assertEquals(java.util.Arrays.asList(AppSource.VIDEO_API_HOST, AppSource.RETRY_HOST, AppSource.VIDEO_API_HOST), hosts);
        assertEquals(3, registrations.get());
        assertEquals(DEVICE_ID, used.get(0).deviceId);
        assertEquals("7432109876543219999", used.get(1).deviceId);
        assertSame("最后的备用身份可供后续请求使用", used.get(2), PlaybackDevice.get());
        assertTrue(bodies.get(0).contains("\"device_level\":1"));
    }

    @Test public void emptyPlaybackRetriesRemainBounded() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        PlaybackDevice.resetForTest((url, headers, body) -> {
            registrations.incrementAndGet();
            return okReply();
        });
        AtomicInteger posts = new AtomicInteger();
        AppSource.setPlaybackPostForTest((body, identity, host) -> {
            posts.incrementAndGet();
            throw new AppSource.EmptyPlaybackResponseException();
        });
        try {
            AppSource.fetch("7000000000000000001", "auto");
            fail();
        } catch (AppSource.EmptyPlaybackResponseException expected) {
            // 两轮主备路径均已失败，停止重试。
        }
        assertEquals(4, posts.get());
        assertEquals(4, registrations.get());
    }

    @Test public void failedRenewalStillAttemptsTheBackupHost() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        PlaybackDevice.resetForTest((url, headers, body) -> {
            if (registrations.incrementAndGet() > 1) throw new IOException("offline");
            return okReply();
        });
        AtomicInteger posts = new AtomicInteger();
        AppSource.setPlaybackPostForTest((body, identity, host) -> {
            posts.incrementAndGet();
            throw new AppSource.EmptyPlaybackResponseException();
        });
        try {
            AppSource.fetch("7000000000000000001", "auto");
            fail();
        } catch (AppSource.EmptyPlaybackResponseException e) {
            assertTrue(e.getSuppressed().length >= 1);
        }
        assertEquals(2, posts.get());
        assertEquals(2, registrations.get());
    }

    @Test public void renewReusesIdentityAlreadyReplacedByAnotherCaller() throws Exception {
        AtomicInteger registrations = new AtomicInteger();
        PlaybackDevice.resetForTest((url, headers, body) -> {
            registrations.incrementAndGet();
            return okReply();
        });
        PlaybackDevice.Identity rejected = PlaybackDevice.get();
        PlaybackDevice.Identity renewed = PlaybackDevice.renew(rejected);
        assertNotSame(rejected, renewed);
        assertSame(renewed, PlaybackDevice.renew(rejected));
        assertEquals(2, registrations.get());
    }

    // ==================== 内联片源模型 ====================

    @Test public void inlineVideoListIsUsedWithoutFallbackRequest() throws Exception {
        List<Object> list = Json.arr();
        list.add(item("bytevc1", 1080, 1920, "https://v1.qznovelvod.com/hevc.mp4", VALID_SPADE));
        list.add(item("h264", 720, 1280, "https://v1.qznovelvod.com/avc.mp4", VALID_SPADE));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        // 若误走 fallback，会请求这个不受信任地址并抛出异常
        model.put("fallback_api", "https://evil.invalid/fallback");

        Stream.VideoTrack t = AppSource.parseModel("7600000000000000001", response("7600000000000000001", model), "auto");
        assertEquals("自动模式 H.264 优先", "https://v1.qznovelvod.com/avc.mp4", t.url);
        assertTrue(t.encrypt);
        assertEquals(VALID_SPADE, t.spadeA);
        assertEquals("720p", t.definition);
    }

    @Test public void inlineModelMayBeJsonStringAndHonoursRequestedQuality() throws Exception {
        List<Object> list = Json.arr();
        list.add(item("h264", 540, 960, "https://v1.qznovelvod.com/540.mp4", VALID_SPADE));
        list.add(item("bytevc1", 1080, 1920, "https://v1.qznovelvod.com/1080.mp4", VALID_SPADE));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        Stream.VideoTrack t = AppSource.parseModel("7600000000000000002",
                responseRaw("7600000000000000002", Json.stringify(model)), "1080p");
        assertEquals("https://v1.qznovelvod.com/1080.mp4", t.url);
    }

    @Test public void inlineEncryptedItemWithoutSpadeIsNotPlayedAsPlaintext() throws Exception {
        List<Object> list = Json.arr();
        list.add(item("h264", 720, 1280, "https://v1.qznovelvod.com/nokey.mp4", null));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        try {
            AppSource.parseModel("7600000000000000003", response("7600000000000000003", model), "auto");
            fail("缺少解密信息的内联片源不得当作明文播放");
        } catch (IllegalStateException expected) {
            assertFalse(expected.getMessage().contains("nokey"));
        }

        Map<String, Object> plain = item("h264", 720, 1280, "https://v1.qznovelvod.com/plain.mp4", null);
        Map<String, Object> info = Json.obj();
        info.put("encrypt", false);
        plain.put("encrypt_info", info);
        list.set(0, plain);
        Stream.VideoTrack t = AppSource.parseModel("7600000000000000003", response("7600000000000000003", model), "auto");
        assertEquals("https://v1.qznovelvod.com/plain.mp4", t.url);
        assertFalse(t.encrypt);
    }

    @Test public void inlineMediaOutsideCdnAllowListIsRejected() throws Exception {
        List<Object> list = Json.arr();
        list.add(item("h264", 720, 1280, "https://evil.invalid/x.mp4", VALID_SPADE));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        try {
            AppSource.parseModel("7600000000000000004", response("7600000000000000004", model), "auto");
            fail();
        } catch (IllegalStateException expected) {
            assertFalse(expected.getMessage().contains("evil"));
        }
    }

    @Test public void onlyTheRequestedEpisodeModelIsUsed() throws Exception {
        List<Object> list = Json.arr();
        list.add(item("h264", 720, 1280, "https://v1.qznovelvod.com/other.mp4", VALID_SPADE));
        Map<String, Object> model = Json.obj();
        model.put("video_list", list);
        try {
            AppSource.parseModel("7600000000000000005", response("7600000000000000999", model), "auto");
            fail();
        } catch (IllegalStateException expected) {
            assertEquals("App 接口未返回所选分集", expected.getMessage());
        }
    }

    // ==================== 工具 ====================

    private static PlaybackDevice.Reply okReply() {
        return reply(DEVICE_ID, INSTALL_ID);
    }

    private static PlaybackDevice.Reply reply(String deviceId, String installId) {
        return new PlaybackDevice.Reply(200, "{\"server_time\":1,\"device_id\":" + deviceId + ","
                + "\"install_id\":" + installId + ",\"device_id_str\":\"" + deviceId
                + "\",\"install_id_str\":\"" + installId + "\",\"new_user\":1}");
    }

    private static IOException expectRegistrationFailure() {
        try {
            PlaybackDevice.get();
            fail("应注册失败");
            return null;
        } catch (IOException e) {
            assertTrue(e instanceof PlaybackDevice.RegistrationException);
            return e;
        }
    }

    private static Map<String, Object> item(String codec, int w, int h, String url, String spade) {
        Map<String, Object> m = Json.obj();
        m.put("codec_type", codec);
        m.put("definition", Math.min(w, h) + "p");
        m.put("vwidth", (long) w);
        m.put("vheight", (long) h);
        m.put("main_url", url);
        if (spade != null) {
            Map<String, Object> enc = Json.obj();
            enc.put("spade_a", spade);
            enc.put("encrypt", true);
            m.put("encrypt_info", enc);
        }
        return m;
    }

    private static Map<String, Object> response(String vid, Map<String, Object> model) {
        return responseRaw(vid, model);
    }

    private static Map<String, Object> responseRaw(String vid, Object model) {
        Map<String, Object> entry = Json.obj();
        entry.put("video_model", model);
        Map<String, Object> data = Json.obj();
        data.put(vid, entry);
        Map<String, Object> j = Json.obj();
        j.put("code", 0L);
        j.put("data", data);
        return j;
    }

    private static boolean isSignatureHeader(String name) {
        String k = name.toLowerCase();
        return k.equals("x-gorgon") || k.equals("x-khronos") || k.equals("x-argus") || k.equals("x-ladon")
                || k.equals("x-helios") || k.equals("x-medusa") || k.equals("x-tt-dt");
    }

    private static int count(String s, String needle) {
        int n = 0;
        for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
