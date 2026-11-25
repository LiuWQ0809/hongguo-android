package io.github.jackotom.hongguo.android;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.SeekBar;

/** Visual treatment for the touch-player controls. */
final class PlayerChrome {
    private static final int ACCENT = 0xffff5770;

    private PlayerChrome() { }

    static Drawable shade(boolean top) {
        return new GradientDrawable(top ? GradientDrawable.Orientation.TOP_BOTTOM : GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{0xf0000000, 0x33000000});
    }

    static void icon(Button button, int resource) {
        Drawable glyph = button.getContext().getDrawable(resource).mutate();
        glyph.setTintList(new ColorStateList(
            new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_selected}, {}},
            new int[]{0x66ffffff, ACCENT, 0xffffffff}));
        LayerDrawable layer = new LayerDrawable(new Drawable[]{glyph});
        int size = Math.round(23 * button.getResources().getDisplayMetrics().density);
        layer.setLayerSize(0, size, size);
        layer.setLayerGravity(0, Gravity.CENTER);
        button.setBackground(ripple(layer));
        button.setText("");
    }

    static void resume(Button button) {
        icon(button, R.drawable.ic_player_play);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xb8000000);
        button.setBackground(new LayerDrawable(new Drawable[]{disc, button.getBackground()}));
    }

    static void text(Button button) { button.setBackground(ripple(null)); }

    private static RippleDrawable ripple(Drawable content) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xffffffff);
        return new RippleDrawable(ColorStateList.valueOf(0x35ffffff), content, mask);
    }

    static void progress(SeekBar seek) {
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(ColorStateList.valueOf(ACCENT));
    }
}
