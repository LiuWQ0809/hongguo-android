package io.github.jackotom.hongguo.android;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.SurfaceView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

/** Copy only the decoded surface so app controls and overlays stay out of the file. */
final class NativePlaybackScreenshot {
    private NativePlaybackScreenshot() { }

    static void capture(Context context, SurfaceView view, int width, int height, Handler main, Consumer<String> complete) {
        if (width <= 0 || height <= 0 || (long) width * height > 16_777_216L || !view.getHolder().getSurface().isValid()) {
            complete.accept("当前画面暂不能截图"); return;
        }
        final Bitmap bitmap;
        try { bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888); }
        catch (RuntimeException | OutOfMemoryError unavailable) { complete.accept("当前画面分辨率无法截图"); return; }
        try {
            PixelCopy.request(view, bitmap, result -> {
                if (result != PixelCopy.SUCCESS) { bitmap.recycle(); complete.accept("当前画面截图未完成"); return; }
                new Thread(() -> {
                    String status;
                    try { status = save(context.getApplicationContext(), bitmap); }
                    catch (Exception failure) { status = "截图保存未完成"; }
                    finally { bitmap.recycle(); }
                    String message = status;
                    main.post(() -> complete.accept(message));
                }, "hongguo-frame-export").start();
            }, main);
        } catch (RuntimeException unavailable) {
            bitmap.recycle();
            complete.accept("当前画面截图未完成");
        }
    }

    private static String save(Context context, Bitmap bitmap) throws IOException {
        String name = "红果截图-" + System.currentTimeMillis() + ".png";
        if (Build.VERSION.SDK_INT < 29) {
            File parent = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (parent == null) throw new IOException("图片目录不可用");
            File file = new File(parent, name);
            boolean ok = false;
            try (OutputStream stream = new FileOutputStream(file)) { ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            finally { if (!ok) file.delete(); }
            if (!ok) throw new IOException("图像编码未完成");
            return "截图已保存到红果图片目录";
        }
        var resolver = context.getContentResolver();
        var values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/红果截图");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri item = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (item == null) throw new IOException("相册不可写");
        boolean saved = false;
        try {
            try (OutputStream stream = resolver.openOutputStream(item)) {
                if (stream == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("图像编码未完成");
            }
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            saved = resolver.update(item, publish, null, null) == 1;
            if (!saved) throw new IOException("截图发布未完成");
            return "截图已保存到相册";
        } finally { if (!saved) resolver.delete(item, null, null); }
    }
}
