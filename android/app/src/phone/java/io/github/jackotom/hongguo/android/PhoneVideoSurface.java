package io.github.jackotom.hongguo.android;

import android.content.Context;
import android.view.SurfaceView;

/** Fit the decoded display aspect ratio inside the available native surface. */
final class PhoneVideoSurface extends SurfaceView {
    private float aspect;

    PhoneVideoSurface(Context context) { super(context); }

    void aspect(float value) {
        if (aspect == value || !Float.isFinite(value) || value <= 0) return;
        aspect = value;
        requestLayout();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int width = getMeasuredWidth(), height = getMeasuredHeight();
        if (aspect <= 0 || width <= 0 || height <= 0) return;
        if ((float) width / height > aspect) width = Math.round(height * aspect);
        else height = Math.round(width / aspect);
        setMeasuredDimension(Math.max(1, width), Math.max(1, height));
    }
}
