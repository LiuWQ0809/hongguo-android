package io.github.jackotom.hongguo.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.json.JSONObject;

final class EpisodePicker {
    private static final int PAGE_SIZE = 30;
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
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int currentPage = current / PAGE_SIZE;
        showPage(grid, episodes, current, currentPage);
        if (episodes.size() > PAGE_SIZE) {
            Spinner range = new Spinner(activity);
            range.setContentDescription("分集范围");
            var ranges = new ArrayList<String>();
            for (int start = 0; start < episodes.size(); start += PAGE_SIZE) {
                int last = Math.min(start + PAGE_SIZE, episodes.size()) - 1;
                ranges.add("第 " + episodes.get(start).optInt("index") + "–" + episodes.get(last).optInt("index") + " 集");
            }
            ArrayAdapter<String> labels = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, ranges);
            labels.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            range.setAdapter(labels);
            range.setSelection(currentPage);
            range.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> view, View item, int page, long id) {
                    showPage(grid, episodes, current, page);
                }
                @Override public void onNothingSelected(AdapterView<?> view) {}
            });
            panel.addView(range, new LinearLayout.LayoutParams(-1, Math.round(48 * metrics.density)));
        }
        int height = Math.min(Math.round(280 * metrics.density), metrics.heightPixels / 2);
        panel.addView(grid, new LinearLayout.LayoutParams(-1, height));
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("当前第 " + episodes.get(current).optInt("index") + " 集 · 共 " + episodes.size() + " 集")
            .setView(panel).setNegativeButton("关闭", null).create();
        grid.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            select.accept((int) id);
        });
        if (dialog.getWindow() != null) dialog.getWindow().setGravity(Gravity.BOTTOM);
        return dialog;
    }

    private static void showPage(GridView grid, List<JSONObject> episodes, int current, int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, episodes.size());
        grid.setAdapter(new EpisodeGridAdapter(grid.getContext(), episodes.subList(start, end), current, start));
        if (current >= start && current < end) grid.setSelection(current - start);
    }
}
