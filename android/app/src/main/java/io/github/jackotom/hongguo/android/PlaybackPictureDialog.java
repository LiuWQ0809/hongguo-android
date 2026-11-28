package io.github.jackotom.hongguo.android;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.function.BiConsumer;

/** Shared labels and persisted values for phone decoding and display controls. */
final class PlaybackPictureDialog {
    private PlaybackPictureDialog() { }

    static AlertDialog create(Context context, PlaybackPicture current, String decoder,
                              BiConsumer<PlaybackPicture, String> save) {
        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density + .5f);
        fields.setPadding(padding, padding, padding, 0);
        SeekBar brightness = slider(fields, "亮度", 50, 150, current.brightness());
        SeekBar contrast = slider(fields, "对比度", 50, 150, current.contrast());
        SeekBar saturation = slider(fields, "饱和度", 0, 200, current.saturation());
        SeekBar temperature = slider(fields, "色温", -50, 50, current.temperature());
        CheckBox blur = new CheckBox(context);
        blur.setText("模糊填边"); blur.setChecked(current.blur()); fields.addView(blur);
        TextView decoderLabel = new TextView(context); decoderLabel.setText("解码模式"); fields.addView(decoderLabel);
        Spinner selector = new Spinner(context);
        selector.setContentDescription("解码模式");
        String[] modes = {"auto", "hw", "sw"};
        var choices = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
            new String[]{"自动（硬解优先）", "系统硬解", "软件解码（CPU）"});
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selector.setAdapter(choices); selector.setSelection("sw".equals(decoder) ? 2 : "hw".equals(decoder) ? 1 : 0);
        fields.addView(selector);
        ScrollView scroll = new ScrollView(context); scroll.addView(fields);
        return new AlertDialog.Builder(context).setTitle("画面与解码").setView(scroll)
            .setNegativeButton("取消", null)
            .setNeutralButton("恢复默认", (dialog, which) -> save.accept(PlaybackPicture.DEFAULT, "auto"))
            .setPositiveButton("应用", (dialog, which) -> save.accept(new PlaybackPicture(
                brightness.getProgress() + 50, contrast.getProgress() + 50, saturation.getProgress(),
                temperature.getProgress() - 50, blur.isChecked()), modes[selector.getSelectedItemPosition()]))
            .create();
    }

    private static SeekBar slider(LinearLayout fields, String name, int low, int high, int value) {
        TextView label = new TextView(fields.getContext());
        label.setText(name + " " + value); fields.addView(label);
        SeekBar slider = new SeekBar(fields.getContext());
        slider.setContentDescription(name); slider.setMax(high - low); slider.setProgress(value - low);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { label.setText(name + " " + (low + progress)); }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        fields.addView(slider);
        return slider;
    }
}
