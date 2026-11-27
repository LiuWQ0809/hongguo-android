package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.List;
import org.json.JSONObject;

final class EpisodeGridAdapter extends BaseAdapter {
    private final Context context;
    private final List<JSONObject> episodes;
    private final int current, start;

    EpisodeGridAdapter(Context context, List<JSONObject> episodes, int current, int start) {
        this.context = context;
        this.episodes = episodes;
        this.current = current;
        this.start = start;
    }

    @Override public int getCount() { return episodes.size(); }
    @Override public JSONObject getItem(int position) { return episodes.get(position); }
    @Override public long getItemId(int position) { return start + position; }

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        TextView cell = recycled == null ? new TextView(context) : (TextView) recycled;
        boolean selected = start + position == current;
        int number = getItem(position).optInt("index");
        float density = context.getResources().getDisplayMetrics().density;
        cell.setLayoutParams(new AbsListView.LayoutParams(-1, Math.round(46 * density)));
        cell.setGravity(Gravity.CENTER);
        cell.setText(String.valueOf(number));
        cell.setTextSize(16);
        cell.setSelected(selected);
        cell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        cell.setContentDescription(selected ? "正在播放第 " + number + " 集" : "播放第 " + number + " 集");
        cell.setTextColor(selected ? 0xffffe1d5 : 0xffeeebec);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(selected ? 0xff735041 : 0xff343135);
        shape.setCornerRadius(10 * density);
        cell.setBackground(shape);
        return cell;
    }
}
