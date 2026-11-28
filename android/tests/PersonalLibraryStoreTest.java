package io.github.jackotom.hongguo.android;

import java.io.IOException;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PersonalLibraryStoreTest {
    private static final String FIRST = "1234567890123456789", SECOND = "2234567890123456789";
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        var context = new SearchHistoryStoreTest.AppContext();
        var library = new LibraryStore(context);
        var first = new JSONObject().put("seriesId", FIRST).put("title", "测试剧");
        var second = new JSONObject().put("seriesId", SECOND).put("title", "第二部剧");
        var picks = new JSONArray().put(FIRST).put(SECOND).put(FIRST);
        library.toggle(first); library.toggle(second);
        library.history(first, new JSONObject().put("vid", "100000").put("index", 4), 2100);
        library.removeRows("favorites", picks); library.removeRows("favorites", picks);
        check(library.library().getJSONArray("favorites").length() == 0, "重复批删没有恢复追剧");
        check(library.library().getJSONArray("history").length() == 1, "取消追剧保留观看记录");
        var official = new OfficialLibraryTest.Env();
        official.access().record(OfficialLibraryTest.row(FIRST, official.time.get(), 2100));
        official.access().toggleFavorite(first);
        official.access().removeRows("history", Set.of(FIRST));
        check(official.access().rows().length() == 0 && official.access().favorites().length() == 1, "删除账号本机历史保留追剧");
        official.access().record(OfficialLibraryTest.row(FIRST, official.time.get(), 2200));
        official.access().clear();
        check(official.access().rows().length() == 0 && official.access().favorites().length() == 1, "清空账号本机历史保留追剧");
        var reservation = new ReservationStore(context);
        reservation.update(FIRST, first, true, true, 3);
        var follow = reservation.snapshot().getJSONObject(0);
        reservation.checked(follow, true, 3);
        check(reservation.messages().length() == 0, "相同集数不生成消息");
        ((SearchHistoryStoreTest.Prefs) context.getSharedPreferences("reservation_library", 0)).failCommits = 1;
        try { reservation.checked(follow, true, 4); throw new AssertionError("失败写入被接受"); }
        catch (IOException expected) { check(reservation.messages().length() == 0, "失败时回滚消息"); }
        reservation.checked(follow, true, 4); reservation.checked(follow, true, 4);
        check(reservation.messages().length() == 1, "同一分集增长只记录一次");
        var message = reservation.messages().getJSONObject(0);
        check(message.getString("seriesId").equals(FIRST) && !message.getBoolean("read"), "通知保留关联剧与未读状态");
        reservation.readMessage(message.getString("messageId"));
        check(new ReservationStore(context.reopen()).messages().getJSONObject(0).getBoolean("read"), "已读可以持久化");
        System.out.println("PERSONAL_LIBRARY_NATIVE_OK guestBatch=true accountIsolation=true reservationPersisted=true deviceTests=not_run");
    }
}
