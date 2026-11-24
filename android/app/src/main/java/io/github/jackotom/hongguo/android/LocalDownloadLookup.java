package io.github.jackotom.hongguo.android;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

final class LocalDownloadLookup {
    private LocalDownloadLookup() {}

    static Uri find(Context context, String sid, String vid) throws Exception {
        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        JSONArray records = new LibraryStore(context).downloads();
        for (int i = 0; i < records.length(); i++) {
            JSONObject row = records.optJSONObject(i); if (row == null) continue;
            JSONObject s = row.optJSONObject("series"), e = row.optJSONObject("episode");
            if (s == null || e == null || !s.optString("seriesId").equals(sid) || !e.optString("vid").equals(vid)) continue;
            File preparedFile = LibraryStore.preparedFile(context, row);
            if (preparedFile != null) return Uri.fromFile(preparedFile);
            if (row.has("preparedFile")) continue;
            long id = row.optLong("id", -1);
            try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (cursor != null && cursor.moveToFirst() && cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri uri = manager.getUriForDownloadedFile(id);
                    if (uri != null && "content".equals(uri.getScheme())) {
                        try (ParcelFileDescriptor file = context.getContentResolver().openFileDescriptor(uri, "r")) {
                            if (file != null) return uri;
                        } catch (Exception ignored) { /* Try another completed copy, then stream/cache. */ }
                    }
                }
            }
        }
        return null;
    }
}
