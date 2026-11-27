package io.github.jackotom.hongguo.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.GridView;
import java.util.List;
import java.util.function.IntConsumer;
import org.json.JSONObject;

final class EpisodePicker {
    private EpisodePicker() {}

    static AlertDialog create(Activity activity, List<JSONObject> episodes, int current, IntConsumer select) {
        var metrics = activity.getResources().getDisplayMetrics();
        int spacing = Math.round(8 * metrics.density);
        GridView grid = new GridView(activity);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Math.round(48 * metrics.density));
        grid.setHorizontalSpacing(spacing);
        grid.setVerticalSpacing(spacing);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(spacing * 2, spacing, spacing * 2, spacing);
        grid.setAdapter(new EpisodeGridAdapter(activity, episodes, current, 0));
        grid.setSelection(current);

        FrameLayout panel = new FrameLayout(activity);
        int height = Math.min(Math.round(290 * metrics.density), metrics.heightPixels / 2);
        panel.addView(grid, new FrameLayout.LayoutParams(-1, height));
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("选集 · " + episodes.size() + " 集")
            .setView(panel).setNegativeButton("关闭", null).create();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            select.accept((int) id);
        });
        if (dialog.getWindow() != null) dialog.getWindow().setGravity(Gravity.BOTTOM);
        return dialog;
    }
}
