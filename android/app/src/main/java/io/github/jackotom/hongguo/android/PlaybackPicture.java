package io.github.jackotom.hongguo.android;

import android.content.Context;

record PlaybackPicture(int brightness, int contrast, int saturation, int temperature, boolean blur) {
    static final PlaybackPicture DEFAULT = new PlaybackPicture(100, 100, 100, 0, false);
    private static final String STORE = "playback_picture_v1";

    PlaybackPicture {
        if (brightness < 50 || brightness > 150 || contrast < 50 || contrast > 150
                || saturation < 0 || saturation > 200 || temperature < -50 || temperature > 50)
            throw new IllegalArgumentException("画面参数超出范围");
    }

    static PlaybackPicture read(Context context) {
        var prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        try {
            return new PlaybackPicture(prefs.getInt("brightness", 100), prefs.getInt("contrast", 100),
                prefs.getInt("saturation", 100), prefs.getInt("temperature", 0), prefs.getBoolean("blur", false));
        } catch (IllegalArgumentException | ClassCastException invalid) {
            return DEFAULT;
        }
    }

    void save(Context context) {
        if (!context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putInt("brightness", brightness).putInt("contrast", contrast).putInt("saturation", saturation)
                .putInt("temperature", temperature).putBoolean("blur", blur).commit())
            throw new IllegalStateException("画面设置保存未完成");
    }

    float[] matrix() {
        float gain = contrast / 100f, chroma = saturation / 100f, heat = temperature / 200f;
        float[] y = {.2126f, .7152f, .0722f}, white = {1 + heat, 1, 1 - heat}, matrix = new float[16];
        for (int channel = 0; channel < 3; channel++) {
            for (int sample = 0; sample < 3; sample++)
                matrix[sample * 4 + channel] = gain * white[channel] * ((1 - chroma) * y[sample] + (sample == channel ? chroma : 0));
            matrix[12 + channel] = brightness / 100f * .5f - gain * .5f;
        }
        matrix[15] = 1;
        return matrix;
    }
}
