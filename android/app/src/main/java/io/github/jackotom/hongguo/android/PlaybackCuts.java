package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Fixed time cuts are opt-in and bounded against the decoder's actual duration. */
final class PlaybackCuts {
    private static final int MAX_SECONDS = 180;
    static final PlaybackCuts DEFAULT = new PlaybackCuts(0, 0);
    private final int intro, outro;

    PlaybackCuts(int intro, int outro) {
        this.intro = intro;
        this.outro = outro;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("playback_cuts_v1", Context.MODE_PRIVATE);
    }

    static PlaybackCuts read(Context context) {
        SharedPreferences prefs = preferences(context);
        try {
            return new PlaybackCuts(Math.max(0, Math.min(MAX_SECONDS, prefs.getInt("intro", 0))),
                Math.max(0, Math.min(MAX_SECONDS, prefs.getInt("outro", 0))));
        } catch (ClassCastException corrupt) { return DEFAULT; }
    }

    JSONObject toJson() throws JSONException { return new JSONObject().put("intro", intro).put("outro", outro); }

    private static int seconds(JSONObject values, String key) throws JSONException {
        Object raw = values.opt(key);
        if (!(raw instanceof Number)) throw new JSONException("片头片尾秒数无效");
        double value = ((Number) raw).doubleValue();
        if (value != Math.rint(value) || value < 0 || value > MAX_SECONDS) throw new JSONException("片头片尾须为 0 到 180 秒整数");
        return (int) value;
    }

    static synchronized JSONObject save(Context context, JSONObject values) throws JSONException {
        int intro = seconds(values, "intro"), outro = seconds(values, "outro");
        SharedPreferences prefs = preferences(context);
        PlaybackCuts previous = read(context);
        if (!prefs.edit().putInt("intro", intro).putInt("outro", outro).commit()) {
            prefs.edit().putInt("intro", previous.intro).putInt("outro", previous.outro).commit();
            throw new JSONException("片头片尾设置保存未完成");
        }
        return new PlaybackCuts(intro, outro).toJson();
    }

    int start(int position, int duration) {
        return position > 0 ? position : bounded(intro, duration);
    }

    boolean finishesAt(int position, int duration) {
        int tail = bounded(outro, duration);
        return tail > 0 && position >= duration - tail;
    }

    private static int bounded(int seconds, int duration) {
        return seconds * 1000 <= duration / 4 ? seconds * 1000 : 0;
    }
}
