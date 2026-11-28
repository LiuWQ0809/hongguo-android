#!/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
JSON_JAR="${JSON_JAR:-$HOME/.m2/repository/com/vaadin/external/google/android-json/0.0.20131108.vaadin1/android-json-0.0.20131108.vaadin1.jar}"
TMP="$(mktemp -d -t hongguo-native-directory.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/android/app/src/main/java/io/github/jackotom/hongguo/android"
cat > "$TMP/DirectoryStubs.java" <<'JAVA'
package io.github.jackotom.hongguo.android;
import org.json.JSONObject;
final class OfficialApiClient implements AutoCloseable {
    OfficialApiClient(JSONObject state) { }
    static JSONObject newSession() { return new JSONObject(); }
    static boolean validID(String id) { return id.matches("[1-9][0-9]{0,29}"); }
    void deadlineNanos(long deadline) { }
    JSONObject cloud(String path, String method, JSONObject query, JSONObject body, boolean gzip) { throw new AssertionError("offline fixture"); }
    public void close() { }
}
final class EpisodeResolver { static String publicHttpsUrl(String value) { return ""; } }
final class PlaybackSources { static boolean isRetiredSeries(String sid) { return sid.matches("99[0-9]{8}"); } }
final class LibraryStore { static JSONObject series(JSONObject value) { return value; } }
JAVA
"$JAVA_HOME/bin/javac" --release 17 -encoding UTF-8 -cp "$JSON_JAR" -d "$TMP" "$TMP/DirectoryStubs.java" \
  "$SRC/NativeSeriesDetail.java" "$SRC/NativeDirectoryFallback.java" "$SRC/RecommendationQueue.java" "$ROOT/android/tests/NativeDirectoryFallbackTest.java"
"$JAVA_HOME/bin/java" -cp "$TMP:$JSON_JAR" io.github.jackotom.hongguo.android.NativeDirectoryFallbackTest
