# Sources and notices

Cloud favorites reads prefer the light video shelf and fall back to the legacy shelf, adapted from
OOIUa/hongguo-android v0.7.27 (`a8d1b342a9a824784cf45070f15541f073cdddcb`), GPL-3.0-only.
The local variant preserves account-session checks and handles detail-read failures without dropping IDs.
https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.27

This Android application is distributed under GPL-3.0-only. See LICENSE.

The protocol and media core are adapted from sunnysky123/hongguo-web:

- Java baseline: v1.0.7, commit a54a5de88c77ea81b8d5d39bdfec51cecebb40c3.
- Current behavior reference: v1.1.4, commit 71d25c2e23c2663d9bc7f1e52a4ed44fd878050b.
- Source: https://github.com/sunnysky123/hongguo-web
- Copyright (c) 2026 sunnysky123. The upstream MIT notice is preserved in app/src/main/assets/licenses/hongguo-web-MIT.txt.

Upstream PureSign, Sm3 and WebSource identify jackotom/hongguo-drama as an implementation source under GPL-3.0. These attribution comments are preserved, and the GPL-3.0 text is included in app/src/main/assets/licenses/GPL-3.0.txt.

- Source: https://github.com/jackotom/hongguo-drama

Android adaptations replace desktop configuration, console handling, HTTP service routing, process launching and FFmpeg invocation. The Android application calls the protocol and media core in its own process and reads prepared media as local files.

AndroidX, Kotlin, Kotlin Coroutines and Coil are used as build/runtime dependencies. Their original dependency metadata and license notices remain applicable. The Gradle wrapper is from Gradle 8.13.

Complete application source, build scripts and modifications accompany the test APK in this project directory. No signing private key, local SDK configuration or build cache is part of the source distribution.

## Personal account integration (0.6.0)

The native account protocol in `app/src/main/java/com/hongguo/account/protocol/` is adapted from:

- tsukiyo-zero/hongguoTV, commit `34c793055fa897075d9061dc7d62ee84031ef855` (2026-10-24), GPL-3.0-only.
  https://github.com/tsukiyo-zero/hongguoTV/tree/34c793055fa897075d9061dc7d62ee84031ef855/kotlin-tv
- Its original account device/login reference is charlie901208/HGTV, `feat/phone-login` (GPL-3.0-only), as identified in the upstream NOTICE.
  https://github.com/charlie901208/HGTV/tree/feat/phone-login
- Account endpoint and parsing references include pan8664716/HGTV, commit `ad69d589bbf472e72ce4f2e985a376f8a10bf99a` (GPL-3.0-only).
  https://github.com/pan8664716/HGTV/tree/ad69d589bbf472e72ce4f2e985a376f8a10bf99a
- The account request signer originated from the Kotlin port of woshishiq1/drpys, commit `22261adfa31435e3b3ef5a730b8a2a75bcaf1715`, GPL-3.0.
  https://github.com/woshishiq1/drpys/tree/22261adfa31435e3b3ef5a730b8a2a75bcaf1715

Original authors' copyrights and SPDX notices are retained. The Compose account UI, encrypted account state, lifecycle isolation and durable progress scheduling are adapted or implemented for this application. No private account samples, credentials, proprietary desktop binaries or unlicensed account implementations are bundled.

Additional runtime dependencies: OkHttp 4.12.0 and Okio (Apache-2.0), Bouncy Castle bcprov-jdk18on 1.79 (MIT). The upstream notices and license texts are included under `app/src/main/assets/licenses/` and in corresponding source. org.json, MockWebServer and kotlinx-coroutines-test are used only for JVM tests; Android supplies its built-in JSON implementation.

## Playback recovery (0.7.2)

On 2026-10-27 the upstream playback endpoint began rejecting per-request synthetic device IDs and signed requests. `app/src/main/java/com/hongguo/api/service/PlaybackDevice.java` (anonymous device registration via ttEncrypt) and the unsigned playback request / inline `video_list` handling in `AppSource.java` were synchronized from:

- tsukiyo-zero/hongguoTV, commit `388931852ddc614db8b00775a3dc54c5ede6307f` (2026-10-27), GPL-3.0-only — `kotlin-tv/core/.../DeviceRegister.kt` and `PlaybackDeviceSession.kt`.
  https://github.com/tsukiyo-zero/hongguoTV/tree/388931852ddc614db8b00775a3dc54c5ede6307f/kotlin-tv
- jackotom/hongguo-drama 3.9, commit `5febca2e5fca0fff4f4a5f21db50f5de75cbeab6` (2026-10-27), GPL-3.0-only — `src/native/playback-device.js` and `src/native/app-source.js`.
  https://github.com/jackotom/hongguo-drama/tree/5febca2e5fca0fff4f4a5f21db50f5de75cbeab6
- sunnysky123/hongguo-web 1.1.5, commit `79babdfefb6a80284e371b1ba14be897f7988c96` (2026-10-27), MIT — `server/src/service/playback-device.js` and `app-source.js`.
  https://github.com/sunnysky123/hongguo-web/tree/79babdfefb6a80284e371b1ba14be897f7988c96

Local changes: the registered identity (device_id / iid / cdid only) is kept in process memory and is fully separate from the personal account module; no device token, cookie or raw response is stored. Successful registration is shared by playback, prefetch and retries; a failed registration is reused only for a 30-second cooldown instead of until process restart, because an Android process may first play while offline and then stay resident.

## Playback source sync (0.7.10)

- `AppSource.java` playback request now sends `device_level=1` instead of 3. At level 3 the server delivers some episodes' 360P–720P renditions as bytevc2, which Android cannot decode, leaving only 1080P. Synchronized from tsukiyo-zero/hongguoTV, commit `ef61f68105e832fefe79baf069ca2feb2d11ebe6` (2026-10-29, TV 0.1.12), GPL-3.0-only — `kotlin-tv/core/.../ContentRepository.kt`.
  https://github.com/tsukiyo-zero/hongguoTV/tree/ef61f68105e832fefe79baf069ca2feb2d11ebe6/kotlin-tv
- An empty playback response (suspected rejected anonymous identity) now triggers one renewed device registration and a single retry; a second empty response, or a failed renewal, is reported as before. Synchronized from jackotom/hongguo-drama 5.0, commit `1fa218a196478c9bd3908bea9666bc23f23b921f` (2026-10-30), GPL-3.0-only — `src/native/app-source.js` and `src/native/playback-device.js`.
  https://github.com/jackotom/hongguo-drama/tree/1fa218a196478c9bd3908bea9666bc23f23b921f

Local changes: concurrent callers that see the same rejected identity share one renewal; renewal still honors the 30-second registration failure cooldown, and a cancelled playback thread does not retry.

Playback signature request construction was adapted from jackotom/hongguo-drama 5.4, GPL-3.0-only, commit `93e694e3f4ab71b83b58846f70258191112a6ca3`, `src/native/app-source.js`:
https://github.com/jackotom/hongguo-drama/tree/93e694e3f4ab71b83b58846f70258191112a6ca3

The video_tab playback model was adapted from OOIUa/hongguo-android v0.7.12, GPL-3.0-only, commit `8f7184574925`, whose upstream reference is tsukiyo-zero/hongguoTV (GPL-3.0-only): https://github.com/OOIUa/hongguo-android/commit/8f7184574925
Local integration retains signed playback as the first choice, then uses video_tab as a fallback.

Selected playback identity, signed GET and ByteVC2 compatibility behavior in the local 0.7.12-local.6 build are adapted from OOIUa/hongguo-android v0.7.25 (GPL-3.0-only): https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.25

## 猜你喜欢 (0.7.10)

`app/src/main/java/com/hongguo/account/protocol/GuessFeed.kt` (request parameters, card parsing) is adapted from tsukiyo-zero/hongguoTV, commit `97c5b1d15dc2233e53f6152918c7e3cc3bbebb84` (2026-10-30), GPL-3.0-only — `kotlin-tv/core/.../RecommendationFeed.kt`, whose endpoint path and card shape reference Xanerix/guoapp `native/core/provider_hongguo_recommendations.go`.
  https://github.com/tsukiyo-zero/hongguoTV/tree/97c5b1d15dc2233e53f6152918c7e3cc3bbebb84/kotlin-tv

Local changes: signed out, the feed is requested anonymously without any cookie or account device; signed in (verified app account only), it carries the account cookie and that account's registered device_id / iid. The request uses the account transport (no redirects, no cookie jar), so the cookie cannot be forwarded to another host. The server session and offset are kept in memory per browse round and discarded on login, logout or account switch. No exposure, click or completion events are reported, and whether the signed-in results match the official App's 猜你喜欢 for the same account has not been verified on a real account.

## 漫剧榜单续页修复（2026-11-14）

参考 [sunnysky123/hongguo-web `1b00f06`](https://github.com/sunnysky123/hongguo-web/commit/1b00f06)（MIT）及 OOIUa/hongguo-android v0.7.28（GPL-3.0-only）：使用 `filter_ids` 排除已展示作品。

## 播放 v5 模型接口（2026-11-16）

移植自 [OOIUa/hongguo-android v0.7.30](https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.30) 和 [N3urda/hongguoTV 6672b55](https://github.com/N3urda/hongguoTV/tree/6672b55d5308ea1ec26d2fe0bc0b2401ef4aa0c3/kotlin-tv)，GPL-3.0-only。包括签名、加密和移动端模型请求；算法参考 drpys `22261ad`。

## 原生播放来源更新（2026-11-22）

播放请求重试、无正文签名与 v5 分集模型 GET 参考 [OOIUa/hongguo-android v0.7.31](https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.31) 和 [v0.7.32](https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.32)，GPL-3.0-only；其 GET 签名设计亦参考 jackotom/hongguo-drama 7.7。此处为按需移植，并非完整同步上游版本。本地保留速率限制、取消请求处理及原有后台播放能力。

## 原生播放与片单兼容更新（2026-11-23）

参考 [OOIUa/hongguo-android v0.7.33](https://github.com/OOIUa/hongguo-android/releases/tag/v0.7.33)（GPL-3.0-only）：解码器回退、媒体节点临时故障重试、明文同轨道备用地址与完整内容摘要校验，以及账号片单资料回填。此处为按需移植，并非完整上游同步；本地保留媒体版本校验、后台连播、锁屏和画中画机制。备用节点仅在播放模型明确提供且可核验为相同内容时用于已开始读取的媒体。

## 公开媒体地址兼容（2026-11-23）

公开单集 JSON 播放地址接口参考 [jackotom/hongguo-drama `ba1fd443`](https://github.com/jackotom/hongguo-drama/commit/ba1fd443f0e1e94bc6cbcbf4bd99459339a2a287) 的问题调研。此处使用自有 Android 请求与校验实现，由 Media3 原生播放。

## 原生分集预加载

`PlaybackPreloadV5.kt` 的预加载请求参数及签名状态参考
[tsukiyo-zero/hongguoTV kotlin-v0.1.38](https://github.com/tsukiyo-zero/hongguoTV/releases/tag/kotlin-v0.1.38)，
许可证为 GPL-3.0-only。
