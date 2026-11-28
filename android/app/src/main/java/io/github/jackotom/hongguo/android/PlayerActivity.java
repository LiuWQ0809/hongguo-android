package io.github.jackotom.hongguo.android;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** A single native player owns the whole queue; Back never dismisses a media popup first. */
public final class PlayerActivity extends Activity {
    private static final java.util.concurrent.atomic.AtomicInteger LIVE_PLAYERS = new java.util.concurrent.atomic.AtomicInteger();
    // A stuck native lookup must not occupy the ordinary resolver or build a queue of retry threads.
    private static final ExecutorService RETRY_LOOKUP = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS,
        new SynchronousQueue<>(), task -> { Thread thread = new Thread(task, "hongguo-source-retry"); thread.setDaemon(true); return thread; });
    private static final long RETRY_LOOKUP_TIMEOUT_MS = 30_000L;
    private boolean registeredPlayer, handedOff;
    static final String BACKGROUND_ID = "backgroundPlaybackId";
    private String backgroundId;
    private Future<?> seasonLookup;
    private PlaybackCuts cuts = PlaybackCuts.DEFAULT;
    private boolean screenshotInProgress;
    static boolean hasLivePlayer() { return LIVE_PLAYERS.get() > 0 || BackgroundPlaybackService.isActive(); }
    private DanmakuController danmaku;
    private DanmakuOverlay danmakuOverlay;
    private Button danmakuButton;
    private boolean danmakuEnabled;
    private String danmakuNotice = "弹幕默认关闭，开启后只读匿名时间轴弹幕";
    private final Handler main = new Handler(Looper.getMainLooper());
    private RecommendationQueue recommendations;
    private RecommendationCursor recommendationCursor;
    private RecommendationPrefetch recommendationPrefetch;
    private final ExecutorService resolver = Executors.newSingleThreadExecutor();
    private Future<?> resolution, prefetch;
    private int prefetchedIndex = -1;
    private final ExecutorService cacheWorker = Executors.newFixedThreadPool(2);
    private PlaybackVideoView video;
    private Object softLayer; private boolean softActive; private PreparedMedia adoptedMedia; private int softToken;
    private FrameLayout playerRoot;
    private PlaybackEngine mediaPlayer;
    private boolean softwareFallback;
    private String quality = "auto", actualQuality = "auto", protectedQuality = "auto";
    private final Set<PreparedMedia> pendingMedia = new HashSet<>();
    private float requestedSpeed = 1f, actualSpeed = Float.NaN;
    private int videoWidth, videoHeight, qualitySerial;
    private volatile QualitySwitch qualitySwitch;
    private TextView title, status, time;
    private LinearLayout top, bottom;
    private LinearLayout phoneContent, phoneActions, heading;
    private ScrollView phoneScroller;
    private Button[] episodeActions;
    private int safeLeft, safeTop, safeRight, safeBottom;
    private Button back, fullscreenButton, resumeOverlay;
    private boolean fullscreen;
    private Runnable systemBackCleanup;
    private PipPlaybackControls pipControls;
    private PlayerHud controlsHud;
    private PlayerSurfaceTaps surfaceTaps;
    private boolean buffering, touchingControls;
    private float swipeX, swipeY;
    private boolean swipeAllowed, tapAllowed;
    private int hudDialogs, wakeKey = KeyEvent.KEYCODE_UNKNOWN;
    private SeekBar seek;
    private Button previous, toggle, next, autoplayButton, qualityButton, speedButton;
    private Button castButton;
    private DlnaCastService castService;
    private BrowserCastService browserCastService;
    private boolean browserCastBound, browserCastSelected, showBrowserPairWhenReady;
    private DlnaSession.Snapshot castSnapshot;
    private boolean castBound;
    private volatile boolean castWasActive;
    private String lastCastError = "";
    private final ExecutorService castDiscoveryWorker = Executors.newSingleThreadExecutor();
    private Future<?> castDiscovery;
    private volatile java.util.concurrent.atomic.AtomicBoolean castDiscoveryCancelled;
    private final DlnaCastService.Listener castListener = value -> onProtocolCastSnapshot(false, value);
    private final BrowserCastService.Listener browserCastListener = value -> onProtocolCastSnapshot(true, value);
    private final ServiceConnection browserCastConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (destroyed) return;
            browserCastService = ((BrowserCastService.LocalBinder) binder).service();
            browserCastService.addListener(browserCastListener);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            browserCastService = null;
            if (destroyed || !browserCastSelected) return;
            castWasActive = false; prepared = false; castSnapshot = null; browserCastSelected = false;
            status.setText("浏览器投屏服务已断开，请重新连接"); updateButtons(); showControls();
        }
    };
    private final ServiceConnection castConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (destroyed) return;
            castService = ((DlnaCastService.LocalBinder) binder).service();
            castService.addListener(castListener);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            castService = null;
            if (destroyed || browserCastSelected) return;
            if (castWasActive) { castWasActive = false; prepared = false; status.setText("投屏服务已断开，请重新连接电视"); }
            castSnapshot = null; updateButtons(); showControls();
        }
    };
    private LibraryStore store;
    private PlaybackCache cache;
    private JSONObject series, episode, queueSeries, protectedEpisode;
    private String queueIdentity = "", protectedSeriesId = "";
    private volatile PlaybackCache.QueueWindow localWindow;
    private PlaybackCache.QueueWindow protectedWindow;
    private final List<JSONObject> queue = new ArrayList<>();
    private volatile int current, generation;
    private int resumeAt;
    private boolean prepared, completed, active, userPaused, dragging, protectedCurrent;
    private volatile boolean destroyed, autoplay;
    private boolean cleanup, offline;
    private String currentKind = "正在连接片源", lastMediaError;
    private boolean currentManual, currentCache, currentWeb;
    private int lastGoodPosition, sourceShortEdge, automaticCeiling, protectedCeiling;
    private Runnable playbackPrepareTimeout, bufferingTimeout;
    private boolean decoderPreparing;
    private long lastProgressSavedAt;
    private final RecoveryBudget recoveryBudget = new RecoveryBudget();
    static final class RecoveryBudget {
        private int generation; private boolean used;
        synchronized void reset(int token) { generation = token; used = false; }
        synchronized void carry(int previous, int next) { if (generation == previous) generation = next; }
        synchronized boolean take(int token, boolean offline, boolean manual, boolean unsupported) {
            if (token != generation || used || offline || manual || unsupported) return false;
            used = true; return true;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LIVE_PLAYERS.incrementAndGet(); registeredPlayer = true;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        try {
            store = new LibraryStore(this); cache = PlaybackCache.get(this);
            series = LibraryStore.series(new JSONObject(getIntent().getStringExtra("series"))); queueSeries = series;
            JSONObject selected = LibraryStore.episode(new JSONObject(getIntent().getStringExtra("episode")));
            String descriptor = getIntent().getStringExtra("playlist");
            if (descriptor != null && !descriptor.isEmpty()) {
                JSONArray episodes = PlaybackPlaylist.parse(new JSONObject(descriptor), series, selected);
                for (int i = 0; i < episodes.length(); i++) queue.add(episodes.getJSONObject(i));
            } else {
                JSONArray episodes = new JSONArray(getIntent().getStringExtra("episodes"));
                if (episodes.length() == 0 || episodes.length() > 1000) throw new Exception("分集队列无效");
                Set<String> ids = new HashSet<>(); Set<Integer> indexes = new HashSet<>();
                for (int i = 0; i < episodes.length(); i++) {
                    JSONObject item = LibraryStore.episode(episodes.getJSONObject(i));
                    if (!ids.add(item.getString("vid")) || !indexes.add(item.getInt("index"))) throw new Exception("分集队列重复");
                    queue.add(item);
                }
                queue.sort(Comparator.comparingInt(item -> item.optInt("index")));
            }
            current = PlaybackPlaylist.find(queue, series.getString("seriesId"), selected.getString("vid"), selected.getInt("index"), queueSeries);
            if (current < 0) throw new Exception("分集不在队列中");
            autoplay = state == null ? getIntent().getBooleanExtra("autoplay", true) : state.getBoolean("autoplay", true);
            cleanup = getIntent().getBooleanExtra("cleanupPlaybackCache", true);
            offline = getIntent().getBooleanExtra("download", false);
            String recommendationRows = getIntent().getStringExtra(RecommendationQueue.EXTRA);
            if (!BuildConfig.IS_TV && !offline && recommendationRows != null) {
                recommendations = new RecommendationQueue(new JSONArray(recommendationRows), series.getString("seriesId"));
                recommendationPrefetch = new RecommendationPrefetch(main);
                String cursor = getIntent().getStringExtra(RecommendationCursor.EXTRA);
                if (cursor != null) recommendationCursor = RecommendationCursor.parse(new JSONObject(cursor));
            }
            JSONObject defaults = PlayerOptions.read(this);
            danmakuEnabled = defaults.optBoolean("danmaku", false);
            quality = PlayerOptions.quality(state == null ? getIntent().getStringExtra("quality") == null ? defaults.getString("quality") : getIntent().getStringExtra("quality") : state.getString("quality", defaults.getString("quality")));
            actualQuality = state == null ? quality : MediaQuality.normalize(state.getString("actualQuality", quality));
            automaticCeiling = restoreAutomaticCeiling(state);
            requestedSpeed = PlayerOptions.speed(state == null ? getIntent().getDoubleExtra("speed", defaults.getDouble("speed")) : state.getDouble("speed", defaults.getDouble("speed")));
            boolean restored = restoreSelection(state);
            series = owner(queue.get(current));
            backgroundId = state == null ? null : state.getString(BACKGROUND_ID);
            if (backgroundId == null) backgroundId = getIntent().getStringExtra(BACKGROUND_ID);
            if (backgroundId == null) {
                backgroundId = UUID.randomUUID().toString();
            }
            getIntent().putExtra(BACKGROUND_ID, backgroundId);
            fullscreen = !BuildConfig.IS_TV && (state == null ? getIntent().getBooleanExtra("fullscreen", false) : state.getBoolean("fullscreen"));
            BackgroundPlaybackService.stopOther(backgroundId);
            buildViews();
            if (!BuildConfig.IS_TV && Build.VERSION.SDK_INT >= 33) {
                var dispatcher = getOnBackInvokedDispatcher();
                android.window.OnBackInvokedCallback callback = this::onBackPressed;
                dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
                systemBackCleanup = () -> dispatcher.unregisterOnBackInvokedCallback(callback);
            }
            int position = restored ? Math.max(0, state.getInt("position")) :
                getIntent().getIntExtra("backgroundPosition", store.position(series, queue.get(current)));
            String initial = state == null ? getIntent().getStringExtra("url") : null;
            boolean paused = restored ? state.getBoolean("userPaused", false) : getIntent().getBooleanExtra("backgroundPaused", false);
            if (!resumeFromService()) loadAt(current, initial, position, paused);
            if (isFinishing()) return;
            main.post(tick);
            if (!BuildConfig.IS_TV) {
                castBound = bindService(new Intent(this, DlnaCastService.class), castConnection, BIND_AUTO_CREATE);
                browserCastBound = bindService(new Intent(this, BrowserCastService.class), browserCastConnection, BIND_AUTO_CREATE);
            }
        } catch (Exception error) {
            android.util.Log.e("HongguoPlayer", "播放信息无效", error);
            Toast.makeText(this, "播放信息无效：" + error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextColor(0xffffffff); b.setTextSize(BuildConfig.IS_TV ? 17 : 14);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(48)); b.setPadding(dp(8), 0, dp(8), 0);
        b.setContentDescription(text); b.setOnClickListener(view -> { showControls(); action.run(); }); return b;
    }
    private TextView label(int size) { TextView v = new TextView(this); v.setTextColor(0xffffffff); v.setTextSize(size); return v; }
    private void buildViews() {
        FrameLayout root = new FrameLayout(this); playerRoot = root; root.setBackgroundColor(0xff000000);
        video = new PlaybackVideoView(this); video.setFocusable(true); video.setFocusableInTouchMode(true);
        // PlaybackVideoView fits the decoded aspect ratio within the available bounds.
        root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        installVideoTouch(video);
        if (!BuildConfig.IS_TV) root.setOnTouchListener((view, event) -> true);
        danmakuOverlay = new DanmakuOverlay(this, new DanmakuOverlay.Clock() {
            public long position() { return prepared && video != null ? Math.max(0, video.getCurrentPosition()) : Math.max(0, resumeAt); }
            public boolean playing() { return active && prepared && !completed && !userPaused && !buffering && !dragging && !casting() && !castWasActive && video != null && video.isPlaying(); }
        });
        root.addView(danmakuOverlay, new FrameLayout.LayoutParams(-1, -1));
        danmaku = new DanmakuController(this, main, danmakuOverlay, value -> { danmakuNotice = value; updateDanmakuButton(); });
        if (!BuildConfig.IS_TV) {
            resumeOverlay = button("继续播放", () -> { if (userPaused) togglePlayback(); });
            PlayerChrome.resume(resumeOverlay);
            resumeOverlay.setVisibility(View.GONE);
            root.addView(resumeOverlay, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        }
        top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(dp(8), dp(8), dp(8), dp(8)); top.setBackgroundColor(0xbb101014);
        back = button("‹ 返回", this::onBackPressed);
        if (!BuildConfig.IS_TV) {
            PlayerChrome.icon(back, R.drawable.ic_player_back);
            top.setBackground(PlayerChrome.shade(true));
        }
        top.addView(back, new LinearLayout.LayoutParams(dp(86), dp(48)));
        heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL); heading.setPadding(dp(8), 0, 0, 0);
        title = label(BuildConfig.IS_TV ? 20 : 17); title.setMaxLines(1); status = label(12); status.setMaxLines(2);
        heading.addView(title); heading.addView(status); top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        if (!BuildConfig.IS_TV) {
            castButton = button("投屏", this::showCastChoices);
            PlayerChrome.icon(castButton, R.drawable.ic_player_cast);
            top.addView(castButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
            Button screenshot = button("截图", this::takeScreenshot);
            PlayerChrome.icon(screenshot, R.drawable.ic_player_snapshot);
            top.addView(screenshot, new LinearLayout.LayoutParams(dp(48), dp(48)));
            fullscreenButton = button("全屏", this::toggleFullscreen);
            PlayerChrome.icon(fullscreenButton, R.drawable.ic_player_fullscreen);
            top.addView(fullscreenButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
            updateFullscreen();
        }
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        bottom = new LinearLayout(this); bottom.setOrientation(LinearLayout.VERTICAL); bottom.setBackgroundColor(0xcc101014);
        LinearLayout content = bottom;
        if (BuildConfig.IS_TV) bottom.setPadding(dp(10), dp(8), dp(10), dp(12));
        else {
            bottom.setBackground(PlayerChrome.shade(false));
            phoneContent = new LinearLayout(this); phoneContent.setOrientation(LinearLayout.VERTICAL); content = phoneContent;
            phoneScroller = new ScrollView(this); phoneScroller.setFillViewport(false); phoneScroller.setClipToPadding(false);
            phoneScroller.addView(content, new ScrollView.LayoutParams(-1, -2));
            bottom.addView(phoneScroller, new LinearLayout.LayoutParams(-1, -2));
            title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        time = label(12); content.addView(time);
        seek = new SeekBar(this); seek.setContentDescription("播放进度"); content.addView(seek, new LinearLayout.LayoutParams(-1, dp(BuildConfig.IS_TV ? 40 : 48)));
        if (!BuildConfig.IS_TV) PlayerChrome.progress(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { dragging = true; showControls(); }
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    time.setText(format(progress) + " / " + format(playbackDuration()));
                    // DPAD adjusts a SeekBar without touch-start/touch-stop callbacks.
                    if (!dragging && prepared && !castCommand("seek", progress)) video.seekTo(progress);
                }
            }
            public void onStopTrackingTouch(SeekBar bar) { dragging = false; if (prepared && !castCommand("seek", bar.getProgress())) video.seekTo(bar.getProgress()); showControls(); }
        });
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button select = button("选集", this::selectEpisode);
        if (BuildConfig.IS_TV) {
            previous = button("上一集", () -> move(-1)); toggle = button("暂停", this::togglePlayback); next = button("下一集", () -> move(1));
            autoplayButton = button("连播 开", () -> { autoplay = !autoplay; if (autoplay && prepared && !offline) schedulePrefetch(generation); updateButtons(); });
        }
        qualityButton = button("清晰度 自动", this::selectQuality); speedButton = button("倍速 1×", this::selectSpeed);
        danmakuButton = button("弹幕 关", this::toggleDanmaku);
        if (!BuildConfig.IS_TV) {
            applyDanmakuAppearance();
            danmakuButton.setOnLongClickListener(view -> { showDanmakuAppearance(); return true; });
        }
        Button picture = BuildConfig.IS_TV ? null : button("画面与解码", this::showPicture);
        episodeActions = BuildConfig.IS_TV ? new Button[]{previous, toggle, next, select, autoplayButton}
            : new Button[]{select, danmakuButton, qualityButton, speedButton, picture};
        for (Button b : episodeActions) actions.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
        content.addView(actions); phoneActions = BuildConfig.IS_TV ? null : actions;
        if (BuildConfig.IS_TV) {
            LinearLayout options = new LinearLayout(this);
            for (Button option : new Button[]{qualityButton, speedButton, danmakuButton})
                options.addView(option, new LinearLayout.LayoutParams(0, dp(46), 1));
            content.addView(options);
        } else {
            PlayerChrome.icon(select, R.drawable.ic_player_episodes);
            PlayerChrome.icon(danmakuButton, R.drawable.ic_player_danmaku);
            PlayerChrome.text(qualityButton);
            PlayerChrome.text(speedButton);
            PlayerChrome.icon(picture, R.drawable.ic_player_picture);
        }
        updateDanmakuButton();
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        if (!BuildConfig.IS_TV) installPhoneLayout();
        PlayerHud.Scheduler hudScheduler = new PlayerHud.Scheduler() {
            public void cancel(Runnable action) { main.removeCallbacks(action); }
            public void later(Runnable action, long delay) { main.postDelayed(action, delay); }
        };
        controlsHud = new PlayerHud(hudScheduler, visible -> {
            boolean show = visible && !isInPictureInPictureMode();
            top.setVisibility(show ? View.VISIBLE : View.GONE);
            bottom.setVisibility(show ? View.VISIBLE : View.GONE);
            if (!visible && video != null) video.requestFocus();
        });
        if (!BuildConfig.IS_TV) {
            surfaceTaps = new PlayerSurfaceTaps(hudScheduler, this::togglePictureControls, () -> video.performClick(),
                ViewConfiguration.getDoubleTapTimeout(), ViewConfiguration.get(this).getScaledDoubleTapSlop());
        }
        video.requestFocus(); showControls();
    }
    private void installPhoneLayout() {
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            playerRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                setPhoneSafeArea(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
        } else if (Build.VERSION.SDK_INT >= 28) {
            playerRoot.setOnApplyWindowInsetsListener((view, insets) -> {
                DisplayCutout cutout = insets.getDisplayCutout();
                setPhoneSafeArea(cutout == null ? 0 : cutout.getSafeInsetLeft(), cutout == null ? 0 : cutout.getSafeInsetTop(),
                    cutout == null ? 0 : cutout.getSafeInsetRight(), cutout == null ? 0 : cutout.getSafeInsetBottom());
                return insets;
            });
        }
        playerRoot.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyPhoneLayout();
            }
        });
        top.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom - top != oldBottom - oldTop) applyPhoneLayout();
        });
        applyPhoneLayout(); playerRoot.requestApplyInsets();
    }
    private void setPhoneSafeArea(int left, int top, int right, int bottom) {
        safeLeft = Math.max(0, left); safeTop = Math.max(0, top); safeRight = Math.max(0, right); safeBottom = Math.max(0, bottom);
        applyPhoneLayout();
    }
    private void applyPhoneLayout() {
        if (BuildConfig.IS_TV || playerRoot == null || phoneContent == null) return;
        float density = getResources().getDisplayMetrics().density;
        int width = playerRoot.getWidth() > 0 ? playerRoot.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int height = playerRoot.getHeight() > 0 ? playerRoot.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int contentWidth = Math.max(1, width - safeLeft - safeRight);
        int availableHeight = Math.max(1, Math.round((height - safeTop - safeBottom) / density));
        MobileLayoutPolicy.Layout layout = MobileLayoutPolicy.phone(Math.round(contentWidth / density), availableHeight, getResources().getConfiguration().fontScale);
        View focused = playerRoot.findFocus();
        top.setPadding(dp(8) + safeLeft, dp(layout.padding) + safeTop, dp(8) + safeRight, dp(layout.padding));
        back.setMinHeight(dp(layout.buttonHeight)); back.setMinimumHeight(dp(layout.buttonHeight));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(48), -2));
        title.setTextSize(layout.titleSize); status.setTextSize(layout.statusSize); status.setMaxLines(layout.compact ? 1 : 2);
        phoneContent.setPadding(dp(8), dp(layout.padding), dp(8), dp(layout.padding + 4));
        bottom.setPadding(safeLeft, 0, safeRight, safeBottom);
        phoneActions.removeAllViews(); phoneActions.setOrientation(LinearLayout.VERTICAL);
        for (int start = 0; start < episodeActions.length; start += layout.columns) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = start; i < Math.min(episodeActions.length, start + layout.columns); i++) addPhoneButton(row, episodeActions[i], layout.buttonHeight, true);
            phoneActions.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        top.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        phoneContent.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int maxHeight = layout.controlsHeight(availableHeight, (int) Math.ceil(phoneContent.getMeasuredHeight() / density),
            (int) Math.ceil((top.getMeasuredHeight() - safeTop) / density));
        phoneScroller.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(maxHeight)));
        if (focused != null) focused.requestFocus();
    }
    private void addPhoneButton(LinearLayout parent, Button item, int minimumHeight, boolean weighted) {
        if (item.getParent() instanceof ViewGroup) ((ViewGroup) item.getParent()).removeView(item);
        item.setMinHeight(dp(minimumHeight)); item.setMinimumHeight(dp(minimumHeight)); item.setMaxLines(2);
        item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        parent.addView(item, weighted ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2));
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (!BuildConfig.IS_TV && playerRoot != null) { applyPhoneLayout(); playerRoot.requestApplyInsets(); }
    }
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        getIntent().putExtra("fullscreen", fullscreen);
        updateFullscreen();
    }
    private void updateFullscreen() {
        if (fullscreenButton == null) return;
        String label = fullscreen ? "退出全屏" : "全屏";
        if (fullscreenButton.isSelected() != fullscreen) {
            fullscreenButton.setSelected(fullscreen);
            PlayerChrome.icon(fullscreenButton, fullscreen ? R.drawable.ic_player_fullscreen_exit : R.drawable.ic_player_fullscreen);
        }
        fullscreenButton.setContentDescription(label);
        back.setContentDescription(fullscreen ? "退出全屏并继续播放" : "返回上一页");
        if (isInPictureInPictureMode() || fullscreen && (videoWidth <= 0 || videoHeight <= 0)) return;
        int orientation = !fullscreen ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            : videoWidth > videoHeight ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        if (getRequestedOrientation() != orientation) setRequestedOrientation(orientation);
    }
    private void installVideoTouch(View view) {
        if (!BuildConfig.IS_TV && view instanceof PlaybackVideoView) {
            view.setContentDescription("播放画面，单点显示控制栏，双点播放或暂停，" +
                (recommendations == null ? "上下滑动换集" : "上下滑动换剧"));
            view.setOnClickListener(target -> { togglePlayback(); showControls(); });
            view.setOnTouchListener((target, event) -> true);
            return;
        }
        // Consume the reveal tap so it never also pauses/seeks or activates a newly shown button.
        // A vertical swipe switches episodes: up for next, down for previous, short-drama style.
        final float[] down = new float[2];
        view.setOnTouchListener((target, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { down[0] = event.getRawX(); down[1] = event.getRawY(); return true; }
            if (action == MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
                if (Math.abs(dy) >= dp(72) && Math.abs(dy) >= Math.abs(dx) * 2) {
                    if (dy < 0 && current + 1 < queue.size()) { move(1); return true; }
                    if (dy > 0 && current > 0) { move(-1); return true; }
                    Toast.makeText(this, dy < 0 ? "已经是最后一集" : "已经是第一集", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        });
    }
    private boolean canHideControls() {
        if (casting()) return false;
        if (!active || destroyed || isFinishing() || !prepared || completed || userPaused || buffering || dragging || (BuildConfig.IS_TV && touchingControls) || hudDialogs > 0 || qualitySwitch != null || video == null) return false;
        try { return video.isPlaying(); } catch (RuntimeException ignored) { return false; }
    }
    private void updateResumeOverlay() {
        if (resumeOverlay == null) return;
        boolean show = prepared && userPaused && !completed && !casting() && !isInPictureInPictureMode();
        resumeOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
    private void updateControlsState() {
        if (controlsHud != null) controlsHud.playback(canHideControls());
        updateResumeOverlay();
    }
    private void showControls() {
        if (controlsHud == null) return;
        controlsHud.interact(); updateControlsState();
    }
    private void togglePictureControls() {
        if (!active || destroyed || isFinishing() || isInPictureInPictureMode()) return;
        if (!prepared || completed || buffering || qualitySwitch != null || hudDialogs > 0 || casting()) showControls();
        else controlsHud.toggle();
    }
    private void showPlayerDialog(AlertDialog dialog) {
        ++hudDialogs; showControls();
        dialog.setOnDismissListener(ignored -> { hudDialogs = Math.max(0, hudDialogs - 1); showControls(); });
        dialog.show();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        boolean surfaceTap = false;
        boolean phoneTouch = surfaceTaps != null && !isInPictureInPictureMode();
        if (phoneTouch) {
            if (action == MotionEvent.ACTION_DOWN) {
                swipeX = event.getRawX();
                swipeY = event.getRawY();
                swipeAllowed = !underTouch(top, swipeX, swipeY) && !underTouch(bottom, swipeX, swipeY);
                tapAllowed = swipeAllowed && (resumeOverlay == null || !underTouch(resumeOverlay, swipeX, swipeY));
                if (!tapAllowed) surfaceTaps.cancel();
            } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
                swipeAllowed = tapAllowed = false;
                surfaceTaps.cancel();
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - swipeX, dy = event.getRawY() - swipeY;
                if (Math.hypot(dx, dy) > dp(12)) {
                    if (tapAllowed) surfaceTaps.cancel();
                    tapAllowed = false;
                }
                if (action == MotionEvent.ACTION_UP && swipeAllowed && Math.abs(dy) >= dp(72) && Math.abs(dy) >= Math.abs(dx) * 2) {
                    swipeAllowed = tapAllowed = false;
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    touchingControls = false;
                    controlsHud.hold(false);
                    switchBySwipe(dy < 0 ? 1 : -1);
                    showControls();
                    return true;
                }
                if (action == MotionEvent.ACTION_UP) {
                    surfaceTap = tapAllowed && event.getEventTime() - event.getDownTime() < ViewConfiguration.getLongPressTimeout();
                    swipeAllowed = tapAllowed = false;
                }
            }
        }
        if (action == MotionEvent.ACTION_DOWN) touchingControls = true;
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) touchingControls = false;
        // Keep the hit targets stable until the child has handled the event.
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (phoneTouch) controlsHud.hold(touchingControls);
            else showControls();
        }
        if (surfaceTap) surfaceTaps.tap(event.getRawX(), event.getRawY());
        return handled;
    }
    private static boolean underTouch(View view, float x, float y) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        return view.isShown() && view.getGlobalVisibleRect(bounds) && bounds.contains((int) x, (int) y);
    }
    private void switchBySwipe(int delta) {
        if (recommendations != null) { switchRecommendation(delta); return; }
        if (current + delta >= 0 && current + delta < queue.size()) {
            if (prefetch != null && prefetchedIndex != current + delta) prefetch.cancel(true);
            move(delta);
        }
        else Toast.makeText(this, delta > 0 ? "已经是最后一集" : "已经是第一集", Toast.LENGTH_SHORT).show();
    }
    private void switchRecommendation(int direction) {
        if (casting() || castWasActive) {
            Toast.makeText(this, "结束投屏后可滑动换剧", Toast.LENGTH_SHORT).show(); return;
        }
        String target = recommendations.neighbor(direction);
        if (target == null) {
            if (direction > 0 && recommendationCursor != null) loadRecommendationPage(3);
            else Toast.makeText(this, direction > 0 ? "推荐已经刷完" : "已到推荐开头", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "正在打开相邻短剧", Toast.LENGTH_SHORT).show();
        recommendationPrefetch.load(target, this::openRecommendation);
    }
    private void loadRecommendationPage(int attempts) {
        RecommendationCursor cursor = recommendationCursor;
        Toast.makeText(this, "正在加载下一批推荐", Toast.LENGTH_SHORT).show();
        recommendationPrefetch.loadPage(cursor, page -> {
            if (!active || destroyed || isFinishing() || isInPictureInPictureMode() || casting() || castWasActive) return;
            try {
                if (page == null) throw new java.io.IOException("推荐页未返回");
                RecommendationCursor next = cursor.after(page);
                recommendations.append(page);
                recommendationCursor = next;
                String target = recommendations.neighbor(1);
                if (target != null) recommendationPrefetch.load(target, this::openRecommendation);
                else if (next != null && attempts > 1) loadRecommendationPage(attempts - 1);
                else Toast.makeText(this, next == null ? "推荐已经刷完" : "本批内容已看，可继续上滑", Toast.LENGTH_SHORT).show();
            } catch (Exception unavailable) {
                recommendationPrefetch.cancel();
                Toast.makeText(this, "下一批推荐暂未加载，可以再次上滑重试", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openRecommendation(JSONObject detail) {
        if (!active || destroyed || isFinishing() || isInPictureInPictureMode() || casting() || castWasActive) return;
        try (UpdateOperationGate.Lease held = UpdateInstallCheckpoint.operation()) {
            JSONObject owner = LibraryStore.series(detail);
            JSONArray episodes = detail.getJSONArray("episodes");
            JSONObject entry = RecommendationQueue.startEpisode(episodes, store.seriesHistory(owner.getString("seriesId")));
            JSONObject options = new JSONObject().put(RecommendationQueue.EXTRA, recommendations.rows())
                .put("resolveInPlayer", true).put("autoplay", autoplay).put("cleanupPlaybackCache", cleanup)
                .put("quality", quality).put("speed", requestedSpeed);
            if (recommendationCursor != null) options.put(RecommendationCursor.EXTRA, recommendationCursor.toJson());
            Intent next = PlayLauncher.build(this, owner, entry, episodes, null, false, options);
            UpdateInstallCheckpoint.playerLaunching();
            startActivity(next.putExtra("fullscreen", fullscreen));
            finish();
        } catch (Exception failure) {
            recommendationPrefetch.cancel();
            Toast.makeText(this, "相邻短剧暂未打开，可以再次滑动重试", Toast.LENGTH_LONG).show();
        }
    }

    private void updateButtons() {
        updatePipControls();
        if (renderCastControls()) return;
        seek.setEnabled(prepared);
        if (castButton != null) { castButton.setSelected(false); castButton.setContentDescription("投屏"); }
        if (BuildConfig.IS_TV) {
            previous.setEnabled(current > 0); next.setEnabled(current + 1 < queue.size());
            toggle.setEnabled(prepared); toggle.setText(prepared && video.isPlaying() ? "暂停" : completed ? "重播" : "播放");
            autoplayButton.setText(autoplay ? "连播 开" : "连播 关");
            autoplayButton.setContentDescription(autoplay ? "自动连播已开启" : "自动连播已关闭");
        }
        qualityButton.setEnabled(prepared && !offline && !softActive);
        String qualityStatus = offline ? "本地 · 清晰度不可切" : softActive ? "软解播放 · 清晰度不可切" : "清晰度 " + qualityLabel(quality) + (quality.equals(actualQuality) ? "" : " · 当前 " + qualityLabel(actualQuality)) + (qualitySwitch == null ? "" : " · 准备中");
        qualityButton.setText(BuildConfig.IS_TV ? qualityStatus : offline ? "离线" : qualityLabel(actualQuality));
        qualityButton.setContentDescription(qualityStatus);
        speedButton.setEnabled(prepared && !softActive);
        String speedLabel = Float.isNaN(actualSpeed) ? "—×" : rateLabel(actualSpeed);
        String speedStatus = "倍速 " + (Float.isNaN(actualSpeed) ? "待确认" : speedLabel);
        speedButton.setText(BuildConfig.IS_TV ? speedStatus : speedLabel);
        speedButton.setContentDescription(speedStatus);
        updateControlsState();
    }
    private void takeScreenshot() {
        if (screenshotInProgress) return;
        if (!prepared || casting() || softActive || (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
            Toast.makeText(this, "当前画面暂不能截图", Toast.LENGTH_SHORT).show();
            return;
        }
        screenshotInProgress = true;
        NativePlaybackScreenshot.capture(this, video.surface(), videoWidth, videoHeight, new Handler(Looper.getMainLooper()), result -> {
            screenshotInProgress = false;
            if (!destroyed && !isFinishing()) Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
        });
    }
    private void recordProgress(JSONObject selectedSeries, JSONObject selectedEpisode, long position) {
        store.history(selectedSeries, selectedEpisode, position, video == null ? 0 : Math.max(0, video.getDuration()));
        lastProgressSavedAt = SystemClock.elapsedRealtime();
    }
    private void saveProgress() {
        // exitPlayer 已在 finish 前保存。窗口释放后 PlaybackVideoView 可能暂时返回零。
        if (isFinishing()) return;
        if (handedOff || casting() || completed) return; // Keep the recorded finish and the active owner's checkpoint.
        if (prepared && episode != null && video != null) try {
            int position = completed ? 0 : Math.max(0, video.getCurrentPosition());
            // 后台切换时画面可能已释放，避免把刚保存的进度覆盖成零。
            if (!active && !completed && position == 0 && resumeAt > 0) return;
            resumeAt = position;
            recordProgress(owner(episode), episode, resumeAt);
        } catch (RuntimeException transition) { /* A decoder transition is retried at the next checkpoint. */ }
    }
    private void periodicProgress() {
        if (!active || destroyed || !prepared || casting() || dragging || buffering || qualitySwitch != null || video == null || !video.isPlaying()) return;
        long now = SystemClock.elapsedRealtime();
        if (now < lastProgressSavedAt || now - lastProgressSavedAt >= 15000) saveProgress();
    }
    private void watchProgressSeeks(PlaybackEngine player, int token, PlaybackVideoView targetView) {
        player.setOnSeekCompleteListener(mp -> {
            if (destroyed || token != generation || video != targetView || mediaPlayer != mp || !prepared || casting()) return;
            saveProgress();
        });
    }
    private boolean restoreSelection(Bundle state) {
        boolean restored = false;
        if (state != null) {
            String vid = state.getString("vid", ""), sid = state.getString("seriesId", queueSeries.optString("seriesId"));
            int savedIndex = state.getInt("episodeIndex", -1), found = -1;
            if (savedIndex > 0) found = PlaybackPlaylist.find(queue, sid, vid, savedIndex, queueSeries);
            else for (int i = 0; i < queue.size(); i++) {
                JSONObject row = queue.get(i);
                if (row.optString("vid").equals(vid) && owner(row).optString("seriesId").equals(sid)) found = i;
            }
            if (found >= 0) { current = found; restored = true; }
        }
        // Restore against the full legacy descriptor first: a saved later-season
        // selection must not jump back to the Intent's original first season.
        JSONObject selected = queue.get(current), selectedOwner = owner(selected);
        List<JSONObject> sameSeason = PlaybackPlaylist.selectedSeason(queueSeries, queue, selected);
        queue.clear(); queue.addAll(sameSeason); queueSeries = selectedOwner;
        current = PlaybackPlaylist.find(queue, selectedOwner.optString("seriesId"), selected.optString("vid"), selected.optInt("index"), queueSeries);
        queueIdentity = PlaybackPlaylist.identity(queueSeries, queue);
        return restored;
    }
    private void savePlaybackState(Bundle state) {
        state.putBoolean("fullscreen", fullscreen);
        state.putString(BACKGROUND_ID, backgroundId);
        saveProgress(); state.putInt("position", resumeAt); state.putBoolean("autoplay", autoplay);
        state.putString("quality", quality); state.putString("actualQuality", actualQuality); state.putInt("automaticCeiling", automaticCeiling); state.putDouble("speed", requestedSpeed); state.putBoolean("userPaused", userPaused);
        if (episode != null) { state.putString("vid", episode.optString("vid")); state.putString("seriesId", owner(episode).optString("seriesId")); state.putInt("episodeIndex", episode.optInt("index")); }
    }
    private static int restoreAutomaticCeiling(Bundle state) {
        int value = state == null ? 0 : state.getInt("automaticCeiling", 0);
        return value > 0 && value <= 16384 ? value : 0;
    }
    private JSONObject owner(JSONObject target) { return PlaybackPlaylist.owner(queueSeries == null ? series : queueSeries, target); }
    private String mediaKey(JSONObject target, String stageQuality) { return owner(target).optString("seriesId") + ":" + target.optString("vid") + ":" + target.optInt("index") + ":" + stageQuality; }
    private void closeLocalWindow() { PlaybackCache.QueueWindow window = localWindow; localWindow = null; if (window != null) window.close(); }
    private void pruneReleasedWindow(PlaybackCache.QueueWindow captured) {
        // The originating window remains useful after close for a late source/decoder release.
        // A newly opened window protects its own entries and then cleans older retained neighbors.
        if (captured != null) captured.prune();
        PlaybackCache.QueueWindow activeWindow = localWindow;
        if (activeWindow != null && activeWindow != captured) activeWindow.prune();
    }
    private synchronized void releaseCurrent() {
        if (protectedCurrent && protectedEpisode != null) {
            cache.release(protectedSeriesId, protectedEpisode.optString("vid"), protectedEpisode.optInt("index"), protectedQuality, protectedCeiling);
            protectedCurrent = false; protectedEpisode = null; protectedSeriesId = "";
            PlaybackCache.QueueWindow releasedWindow = protectedWindow; protectedWindow = null; pruneReleasedWindow(releasedWindow);
        }
    }
    /** A successful stage owns its exact-quality reservation until UI adoption/cancellation. */
    private static final class PreparedMedia {
        final JSONObject episode, series; final String quality, seriesId; final int ceiling; final PlaybackCache.QueueWindow window; boolean protectedKey;
        Uri uri; String kind; PlaybackSources.Plan plan; boolean manual, cached;
        PreparedMedia(JSONObject series, JSONObject episode, String quality, int ceiling, PlaybackCache.QueueWindow window) { this.ceiling=ceiling; this.series = series; this.seriesId = series.optString("seriesId"); this.episode = episode; this.quality = quality; this.window = window; }
    }
    private synchronized PreparedMedia reserveMedia(JSONObject target, String stageQuality, int ceiling, PlaybackCache.QueueWindow window) throws Exception {
        if (destroyed || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("播放准备已取消");
        PreparedMedia media = new PreparedMedia(owner(target), target, stageQuality, ceiling, window);
        cache.protect(media.seriesId, target.optString("vid"), target.optInt("index"), stageQuality, ceiling);
        media.protectedKey = true; pendingMedia.add(media); return media;
    }
    private synchronized void releasePrepared(PreparedMedia media) {
        if (media == null) return;
        pendingMedia.remove(media);
        if (media.protectedKey) {
            cache.release(media.seriesId, media.episode.optString("vid"), media.episode.optInt("index"), media.quality, media.ceiling);
            media.protectedKey = false;
            pruneReleasedWindow(media.window);
        }
    }
    private synchronized void discardPendingMedia() {
        for (PreparedMedia media : new ArrayList<>(pendingMedia)) releasePrepared(media);
    }
    private synchronized void adoptMedia(PreparedMedia media) {
        releaseCurrent(); pendingMedia.remove(media);
        protectedQuality = media.quality; protectedCeiling = media.ceiling; protectedCurrent = media.protectedKey;
        protectedEpisode = media.episode; protectedSeriesId = media.seriesId; protectedWindow = media.window; media.protectedKey = false;
        actualQuality = media.quality; automaticCeiling = media.ceiling;
    }
    private PreparedMedia prepareMedia(JSONObject target, String stageQuality, String initialUrl, boolean recovering, boolean failedWeb, boolean allowDownloads, int maximumEdge, PlaybackCache.QueueWindow window, int token, boolean boundedRefetch) throws Exception {
        int ceiling=stageCeiling(stageQuality, maximumEdge);
        PreparedMedia media = reserveMedia(target, stageQuality, ceiling, window);
        boolean success = false;
        try {
            Uri local = allowDownloads && offline && initialUrl != null ? Uri.parse(initialUrl) : allowDownloads && (offline || "auto".equals(stageQuality)) ? downloaded(target) : null;
            if (recovering) local = null;
            if (offline && local == null) throw new Exception("这集的手动下载文件已被移除，请返回下载页检查");
            File cached = !recovering && local == null ? cache.find(media.seriesId, target.optString("vid"), target.optInt("index"), stageQuality, ceiling) : null;
            if (local != null) { media.uri = local; media.kind = "手动下载 · 本地播放"; media.manual = true; }
            else if (cached != null) { media.uri = Uri.fromFile(cached); media.kind = "播放缓存 · 本地播放"; media.cached = true; }
            else {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("播放准备已取消");
                PlaybackSources.requireCurrentSource(media.seriesId, target.optString("vid"));
                if (recovering) {
                    final String sid = media.seriesId, vid = target.optString("vid");
                    if (boundedRefetch) {
                        media.plan = resolveRetry(() -> PlaybackSources.resolvePlayback(sid, vid, stageQuality, ceiling, true, failedWeb, RegisteredPlaybackLookup::resolve));
                    } else {
                        cache.invalidate(sid, vid, target.optInt("index"), stageQuality, ceiling);
                        media.plan = PlaybackSources.resolvePlayback(sid, vid, stageQuality, ceiling, true, failedWeb, RegisteredPlaybackLookup::resolve);
                    }
                } else if (initialUrl != null && ceiling == 0) {
                    media.plan = PlaybackSources.find(initialUrl, stageQuality);
                    if (media.plan == null) {
                        String saved = getIntent().getStringExtra("playInfo");
                        JSONObject info = saved == null || saved.isEmpty() ? new JSONObject().put("url", initialUrl).put("source", "web").put("vid", target.optString("vid")) : new JSONObject(saved);
                        info.put("requestedQuality", stageQuality);
                        media.plan = PlaybackSources.register(info, target.optString("vid"), false);
                    }
                } else media.plan = PlaybackSources.resolvePlayback(media.seriesId, target.optString("vid"), stageQuality, ceiling, false, failedWeb, RegisteredPlaybackLookup::resolve);
                if (destroyed || token != generation || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("播放准备已取消");
                if (media.plan.playbackCeiling != ceiling) throw new java.io.IOException("播放片源清晰度边界不匹配");
                if (boundedRefetch) main.post(() -> {
                    if (!destroyed && token == generation) {
                        status.setText("片源已取得 · 正在准备本集下载");
                        showControls();
                    }
                });
                // Do not invalidate a cache entry until a bounded fresh lookup has succeeded.
                if (boundedRefetch) cache.invalidate(media.seriesId, target.optString("vid"), target.optInt("index"), stageQuality, ceiling);
                resetDownloadMarks();
                PlaybackSources.ACTIVE_PROGRESS.set((bytes, totalBytes) -> reportDownloadProgress(token, bytes, totalBytes));
                File ready;
                try { ready = cache.fetchForeground(media.seriesId, target.optString("vid"), target.optInt("index"), media.plan, stageQuality); }
                finally { PlaybackSources.ACTIVE_PROGRESS.remove(); }
                media.uri = Uri.fromFile(ready); media.kind = "片源已准备 · 缓存播放"; media.cached = true;
            }
            success = true; return media;
        } finally { if (!success) releasePrepared(media); }
    }
    /** Whole-file foreground download reporting: percentage, transferred MB and current speed. */
    private long downloadMarkNano, downloadMarkBytes;
    private void resetDownloadMarks() { downloadMarkNano = System.nanoTime(); downloadMarkBytes = 0; }
    private void reportDownloadProgress(int token, long bytes, long totalBytes) {
        if (destroyed) return;
        long now = System.nanoTime(), elapsed = now - downloadMarkNano;
        double mbps = 0;
        if (elapsed > 0 && downloadMarkBytes > 0 && bytes >= downloadMarkBytes) mbps = (bytes - downloadMarkBytes) * 1e9 / elapsed / (1024.0 * 1024.0);
        downloadMarkNano = now; downloadMarkBytes = bytes;
        final String text;
        if (totalBytes > 0) {
            int percent = (int) Math.min(100, bytes * 100 / totalBytes);
            text = percent >= 100 ? "本集下载完成 · 正在准备播放"
                : "正在下载本集 " + percent + "% · " + String.format(java.util.Locale.US, "%.1f/%.1f MB", bytes / 1048576.0, totalBytes / 1048576.0)
                    + (mbps > 0 ? String.format(java.util.Locale.US, " · %.1f MB/s", mbps) : "");
        } else {
            text = "正在下载本集 · 已下 " + String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
                + (mbps > 0 ? String.format(java.util.Locale.US, " · %.1f MB/s", mbps) : "");
        }
        main.post(() -> { if (!destroyed && token == generation) { status.setText(text); showControls(); } });
    }
    private static int stageCeiling(String quality, int maximumEdge) {
        int fixed = AdaptiveQuality.ceiling(quality);
        // Public 720p/1080p preferences retain their established minimum semantics.
        // A ceiling is introduced only by automatic descent or an internal low stage.
        if (maximumEdge <= 0 && !"480p".equals(quality) && !"360p".equals(quality)) return 0;
        return maximumEdge <= 0 ? fixed : fixed <= 0 ? maximumEdge : Math.min(fixed, maximumEdge);
    }
    private static final class RetryLookupFailure extends java.io.IOException {
        RetryLookupFailure(String message) { super(message); }
    }
    private static PlaybackSources.Plan resolveRetry(Callable<PlaybackSources.Plan> lookup) throws Exception {
        final Future<PlaybackSources.Plan> task;
        try { task = RETRY_LOOKUP.submit(lookup); }
        catch (RejectedExecutionException busy) { throw new RetryLookupFailure("上一次重新取源尚未结束，请稍后重试或选择其他分集"); }
        try { return task.get(RETRY_LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS); }
        catch (TimeoutException expired) { throw new RetryLookupFailure("自动重新取源超时（30 秒），可重试或选择其他分集"); }
        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException("播放准备已取消"); }
        catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RetryLookupFailure("自动重新取源未完成，可重试或选择其他分集");
        } finally { task.cancel(true); }
    }
    private static void logRefetch(Throwable first, Throwable last) {
        // Messages, URLs and raw response/throwable objects can contain signed or identifying data.
        android.util.Log.w("HongguoPlayer", "sourceRefetch previous=" + first.getClass().getSimpleName()
            + " final=" + last.getClass().getSimpleName());
    }
    /** One fresh reconnect belongs to a logical selection, never to every lower stage. */
    private PreparedMedia prepareStage(JSONObject target, String stageQuality, String initialUrl, boolean fresh, boolean failedWeb, int token, int maximumEdge, PlaybackCache.QueueWindow window) throws Exception {
        try { return prepareMedia(target, stageQuality, initialUrl, fresh, failedWeb, true, maximumEdge, window, token, false); }
        catch (Exception first) {
            if (offline || fresh || !PlaybackSources.canRecoverPlayback(first) || destroyed || token != generation || Thread.currentThread().isInterrupted()
                    || !recoveryBudget.take(token, false, false, false)) throw first;
            main.post(() -> { if (!destroyed && token == generation) { status.setText("片源暂未取得 · 正在自动重新取源 " + qualityLabel(stageQuality)); showControls(); } });
            try { return prepareMedia(target, stageQuality, null, true, failedWeb, true, maximumEdge, window, token, true); }
            catch (Exception retry) { if (retry != first) retry.addSuppressed(first); logRefetch(first, retry); throw retry; }
            catch (LinkageError | AssertionError retry) { logRefetch(first, retry); throw retry; }
        }
    }
    private void cancelPlaybackWatchdogs() {
        if (playbackPrepareTimeout != null) { main.removeCallbacks(playbackPrepareTimeout); playbackPrepareTimeout = null; }
        if (bufferingTimeout != null) { main.removeCallbacks(bufferingTimeout); bufferingTimeout = null; }
    }
    private boolean validPlaybackCallback(int token, PlaybackVideoView view) {
        return !destroyed && token == generation && view == video && !casting() && !castWasActive;
    }
    private void armPreparationTimeout(int token, PlaybackVideoView view) {
        if (playbackPrepareTimeout != null) main.removeCallbacks(playbackPrepareTimeout);
        decoderPreparing = true;
        final Runnable[] captured = new Runnable[1];
        playbackPrepareTimeout = captured[0] = () -> {
            if (playbackPrepareTimeout != captured[0]) return;
            playbackPrepareTimeout = null;
            if (!validPlaybackCallback(token, view) || !decoderPreparing || prepared || !active) return;
            failCurrentMedia(token, "设备准备视频超时，正在尝试其他片源", false);
        };
        main.postDelayed(playbackPrepareTimeout, 20000);
    }
    private void updateBufferingWatchdog(int token, PlaybackVideoView view) {
        boolean needed = validPlaybackCallback(token, view) && active && prepared && !completed && !userPaused && buffering && qualitySwitch == null;
        if (!needed) { if (bufferingTimeout != null) { main.removeCallbacks(bufferingTimeout); bufferingTimeout = null; } return; }
        if (bufferingTimeout != null) return;
        final Runnable[] captured = new Runnable[1];
        bufferingTimeout = captured[0] = () -> {
            if (bufferingTimeout != captured[0]) return;
            bufferingTimeout = null;
            if (!validPlaybackCallback(token, view) || !active || !prepared || completed || userPaused || !buffering || qualitySwitch != null) return;
            failCurrentMedia(token, "视频持续缓冲超时，正在自动重新取源", false);
        };
        main.postDelayed(bufferingTimeout, 30000);
    }
    private int observedShortEdge() {
        return videoWidth > 0 && videoHeight > 0 ? Math.min(videoWidth, videoHeight) : sourceShortEdge;
    }
    private void failCurrentMedia(int token, String reason, boolean unsupported) {
        if (!validPlaybackCallback(token, video)) return;
        cancelPlaybackWatchdogs(); decoderPreparing = false;
        if (!recoverMedia(token, unsupported ? MediaPlayer.MEDIA_ERROR_UNSUPPORTED : 1, 0, reason) && !lowerCurrentMedia(token, reason))
            playbackError(token, reason + (lastMediaError == null || lastMediaError.equals(reason) ? "" : "；原错误：" + lastMediaError) + "，可重试或选择其他分集");
    }
    private void stageProgress(int token, String stageQuality, int stage, boolean switching) {
        main.post(() -> {
            if (destroyed || token != generation) return;
            String message = stage == 1 ? "正在准备 " : "当前档多次失败 · 自动降低至 ";
            status.setText(message + qualityLabel(stageQuality) + " 视频…" + (switching ? "；当前播放保持" : ""));
            showControls();
        });
    }
    private void loadAt(int position, String initialUrl, int startAt) { loadAt(position, initialUrl, startAt, false); }
    private void loadAt(int position, String initialUrl, int startAt, boolean remainPaused) {
        if (castCommand("select", position)) return;
        loadAtInternal(position, initialUrl, startAt, remainPaused, false, false);
    }
    private void loadAtInternal(int position, String initialUrl, int startAt, boolean remainPaused, boolean recovering, boolean failedWeb) {
        if (destroyed || position < 0 || position >= queue.size()) return;
        if (!recovering) softwareFallback = false;
        cancelSeasonLookup();
        SoftDecoder.stop(softLayer); softLayer = null; softActive = false; adoptedMedia = null;
        cancelQualitySwitch(); cancelPlaybackWatchdogs(); decoderPreparing = false; if (!recovering) saveProgress(); video.stopPlayback(); releaseCurrent(); mediaPlayer = null;
        current = position; episode = queue.get(current); series = owner(episode); final JSONObject target = episode, targetSeries = series;
        cuts = BuildConfig.IS_TV ? PlaybackCuts.DEFAULT : PlaybackCuts.read(this);
        if (!offline) {
            if (localWindow == null) localWindow = cache.openQueueWindow(queueSeries == null ? series : queueSeries, queue, current, cleanup, 1, false);
            else localWindow.moveTo(current);
        }
        final int previousToken = generation, token = ++generation; final String targetQuality = actualQuality;
        final int maximumEdge = automaticCeiling;
        if (danmaku != null) danmaku.suspend();
        final PlaybackCache.QueueWindow targetWindow = localWindow;
        discardPendingMedia();
        if (recovering) recoveryBudget.carry(previousToken, token); else { recoveryBudget.reset(token); lastMediaError = null; currentManual = false; currentCache = false; currentWeb = false; }
        resumeAt = Math.max(0, startAt); lastGoodPosition = resumeAt; prepared = false; completed = false; userPaused = remainPaused;
        actualSpeed = Float.NaN; videoWidth = videoHeight = sourceShortEdge = 0;
        title.setText(series.optString("title") + " · 第 " + target.optInt("index") + " 集");
        currentKind = "正在连接片源"; status.setText(currentKind); time.setText("00:00 / --:--"); seek.setProgress(0); showControls(); updateButtons();
        pruneCache();
        if (resolution != null) resolution.cancel(true);
        resolution = resolver.submit(() -> {
            try {
                AdaptiveQuality.Result<PreparedMedia> result = AdaptiveQuality.prepare(targetQuality,
                    stageQuality -> prepareStage(target, stageQuality, stageQuality.equals(targetQuality) ? initialUrl : null, recovering || !stageQuality.equals(targetQuality), failedWeb, token, stageQuality.equals(targetQuality) ? maximumEdge : stageCeiling(stageQuality, maximumEdge > 0 ? maximumEdge : AdaptiveQuality.ceiling(stageQuality)), targetWindow),
                    error -> !offline && !(error instanceof RetryLookupFailure) && PlaybackSources.canRecoverPlayback(error),
                    () -> !destroyed && token == generation,
                    (stageQuality, stage, error) -> stageProgress(token, stageQuality, stage, false), this::releasePrepared);
                final PreparedMedia media = result.value; final String playedQuality = result.quality;
                main.post(() -> {
                    if (destroyed || token != generation) { releasePrepared(media); return; }
                    adoptMedia(media);
                    currentKind = media.kind; currentManual = media.manual; currentCache = media.cached; currentWeb = false;
                    sourceShortEdge = media.plan != null && media.plan.width > 0 && media.plan.height > 0 ? Math.min(media.plan.width, media.plan.height) : 0; renderStatus();
                    if (BuildConfig.IS_TV && "sw".equals(decoderMode()) && launchSoftDecoder(token, media)) return;
                    openDecoder(token, targetSeries, target, media, playedQuality);
                });
            } catch (Exception error) { main.post(() -> playbackError(token, recovering ? "自动恢复未成功：" + error.getMessage() + "；原错误：" + lastMediaError : error.getMessage())); }
            catch (LinkageError | AssertionError error) {
                android.util.Log.w("HongguoPlayer", "sourcePreparation failure=" + error.getClass().getSimpleName());
                main.post(() -> playbackError(token, "片源准备异常，可重试或选择其他分集"));
            }
        });
    }
    private void openDecoder(int token, JSONObject targetSeries, JSONObject target, PreparedMedia media, String playedQuality) {
        PlaybackVideoView targetView = video;
        targetView.configure(softwareFallback || "sw".equals(decoderMode()));
        bindPlayback(targetView, token, target);
        targetView.setOnPreparedListener(player -> {
            if (destroyed || token != generation || video != targetView) return;
            cancelPlaybackWatchdogs(); decoderPreparing = false;
            videoWidth = player.getVideoWidth(); videoHeight = player.getVideoHeight();
            if (!offline && !media.manual && (!MediaQuality.meets(videoWidth, videoHeight, playedQuality)
                    || media.ceiling > 0 && (videoWidth <= 0 || videoHeight <= 0 || Math.min(videoWidth, videoHeight) > media.ceiling))) {
                failCurrentMedia(token, "当前片源实际分辨率不符合 " + qualityLabel(playedQuality) + " 播放档位", false); return;
            }
            mediaPlayer = player; prepared = true; completed = false;
            adoptedMedia = media;
            watchProgressSeeks(player, token, targetView);
            videoWidth = player.getVideoWidth(); videoHeight = player.getVideoHeight();
            player.setOnVideoSizeChangedListener((mp, width, height) -> { if (video == targetView && token == generation) { videoWidth = width; videoHeight = height; renderStatus(); } });
            try { actualSpeed = player.setVerifiedSpeed(requestedSpeed, false); }
            catch (Exception error) { actualSpeed = player.observedSpeed(); Toast.makeText(this, "设备未接受倍速设置，当前速度以播放器确认为准", Toast.LENGTH_LONG).show(); }
            resumeAt = cuts.start(resumeAt, video.getDuration());
            if (resumeAt > 0 && (video.getDuration() <= 0 || resumeAt < video.getDuration() - 1500)) {
                player.seekPrecisely(resumeAt);
            }
            if (active && !userPaused) video.start();
            recordProgress(targetSeries, target, resumeAt); renderStatus(); updateButtons();
            if (!offline && autoplay) schedulePrefetch(token);
        });
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", media.plan == null ? NetworkGuard.UA : media.plan.userAgent); headers.put("Referer", media.plan == null ? NetworkGuard.REFERER : media.plan.referer);
        try { armPreparationTimeout(token, targetView); targetView.setVideoURI(media.uri, headers); }
        catch (RuntimeException error) { failCurrentMedia(token, "设备打开视频未成功：" + error.getMessage(), false); }
    }
    private void bindPlayback(PlaybackVideoView view, int token, JSONObject target) {
        buffering = false;
        view.setOnInfoListener((player, what, extra) -> {
            if (destroyed || token != generation || video != view) return false;
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && active && !castWasActive && recommendationPrefetch != null) {
                String next = recommendations.neighbor(1);
                if (next != null) recommendationPrefetch.preload(next);
                else if (recommendationCursor != null) recommendationPrefetch.loadPage(recommendationCursor, null);
            }
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START || what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                buffering = what == MediaPlayer.MEDIA_INFO_BUFFERING_START; updateBufferingWatchdog(token, view); updateControlsState();
            }
            return false;
        });
        view.setOnCompletionListener(player -> {
            if (destroyed || token != generation || video != view) return;
            finishEpisode(view.getDuration());
        });
        view.setOnErrorListener((player, what, extra) -> {
            if (token == generation && !destroyed && video == view) {
                String reason = "当前片源或设备解码暂不可用（" + what + "/" + extra + "）";
                boolean unsupported = what == -38 || what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED;
                if (unsupported && !"hw".equals(decoderMode())) {
                    if (BuildConfig.IS_TV && launchSoftDecoder(token, adoptedMedia)) return true;
                    if (!BuildConfig.IS_TV && !view.software() && adoptedMedia != null) {
                        resumeAt = Math.max(lastGoodPosition, prepared ? video.getCurrentPosition() : resumeAt);
                        softwareFallback = true;
                        PreparedMedia media = adoptedMedia;
                        main.post(() -> {
                            if (!validPlaybackCallback(token, view) || adoptedMedia != media) return;
                            video.stopPlayback();
                            prepared = false; mediaPlayer = null;
                            openDecoder(token, series, target, media, media.quality);
                        });
                        return true;
                    }
                }
                failCurrentMedia(token, reason, unsupported);
            }
            return true;
        });
    }
    private void finishEpisode(int duration) {
        if (completed || episode == null) return;
        cancelPlaybackWatchdogs(); decoderPreparing = false;
        completed = true; resumeAt = 0; recordProgress(series, episode, Math.max(0, duration));
        if (autoplay && current + 1 < queue.size()) { loadAt(current + 1, null, 0); return; }
        if (continueSeason(false)) return;
        currentKind = current + 1 == queue.size() ? "本剧已播完" : "本集已播完 · " + (BuildConfig.IS_TV ? "按下一集" : recommendations == null ? "上滑切集" : "选择下一集");
        renderStatus(); showControls(); updateButtons();
    }
    private boolean continueSeason(boolean paused) {
        if (BuildConfig.IS_TV || !autoplay || offline || current + 1 != queue.size()
                || !SeasonContinuation.atFinale(series, episode)) return false;
        JSONObject owner = series, ending = episode;
        int token = generation;
        currentKind = "正在查找下一季";
        renderStatus(); showControls();
        seasonLookup = resolver.submit(() -> {
            SeasonContinuation resolved = null;
            try { resolved = SeasonContinuation.load(this, owner, ending); }
            catch (Exception ignored) { /* Keep the finished season available. */ }
            SeasonContinuation season = resolved;
            main.post(() -> {
                if (seasonLookup == null || token != generation || !completed || destroyed || isFinishing()) return;
                seasonLookup = null;
                if (season != null && active) try (UpdateOperationGate.Lease held = UpdateInstallCheckpoint.operation()) {
                    Intent next = season.intent(this, quality, requestedSpeed, cleanup).putExtra("fullscreen", fullscreen)
                        .putExtra("backgroundPaused", paused);
                    UpdateInstallCheckpoint.playerLaunching();
                    startActivity(next); finish(); return;
                } catch (Exception unavailable) { /* Leave the finished season in place. */ }
                currentKind = "本剧已播完";
                renderStatus(); showControls(); updateButtons();
            });
        });
        return true;
    }

    private void cancelSeasonLookup() {
        if (seasonLookup != null) seasonLookup.cancel(true);
        seasonLookup = null;
    }
    private boolean recoverMedia(int token, int what, int extra, String reason) {
        if (!validPlaybackCallback(token, video)) return false;
        boolean unsupported = what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED;
        if (!recoveryBudget.take(token, offline, currentManual, unsupported)) return false;
        int position = Math.max(0, lastGoodPosition);
        try { int actual = video.getCurrentPosition(); if (actual > 0) position = actual; } catch (RuntimeException ignored) { }
        float confirmed = mediaPlayer == null ? Float.NaN : mediaPlayer.observedSpeed();
        if (!Float.isFinite(confirmed)) confirmed = actualSpeed;
        if (Float.isFinite(confirmed)) requestedSpeed = confirmed;
        lastMediaError = reason; boolean wasPaused = userPaused, wasWeb = currentWeb;
        // Internal re-entry carries the consumed budget; only an explicit selection resets it.
        loadAtInternal(current, null, position, wasPaused, true, wasWeb);
        currentKind = "片源中断 · 正在自动恢复本集"; renderStatus(); return true;
    }
    private boolean lowerCurrentMedia(int token, String reason) {
        if (!validPlaybackCallback(token, video) || offline || currentManual) return false;
        int observed = observedShortEdge();
        String lower = AdaptiveQuality.next(actualQuality, observed); if (lower == null) return false;
        int lowerCeiling = AdaptiveQuality.ceiling(lower);
        if (observed > 0) lowerCeiling = Math.min(lowerCeiling, observed - 1);
        int nextCeiling = automaticCeiling <= 0 ? lowerCeiling : Math.min(automaticCeiling, lowerCeiling);
        int position = Math.max(0, lastGoodPosition);
        try { int actual = video.getCurrentPosition(); if (actual > 0) position = actual; } catch (RuntimeException ignored) { }
        float confirmed = mediaPlayer == null ? Float.NaN : mediaPlayer.observedSpeed();
        if (!Float.isFinite(confirmed)) confirmed = actualSpeed;
        if (Float.isFinite(confirmed)) requestedSpeed = confirmed;
        try { cache.invalidate(owner(episode).optString("seriesId"), episode.optString("vid"), episode.optInt("index"), actualQuality, protectedCeiling); }
        catch (Exception error) { return false; }
        // Decoder/size failures move only down. Carry a consumed reconnect allowance across every stage.
        recoveryBudget.take(token, false, false, false);
        lastMediaError = reason; boolean wasPaused = userPaused, wasWeb = currentWeb;
        actualQuality = lower; automaticCeiling = nextCeiling;
        loadAtInternal(current, null, position, wasPaused, true, wasWeb);
        currentKind = "当前档播放未成功 · 自动降低至 " + qualityLabel(lower); renderStatus(); return true;
    }
    private static String qualityLabel(String value) {
        return "auto".equals(value) ? "自动" : "2160p".equals(value) ? "4K" : value;
    }
    private static String rateLabel(float value) { return String.format(Locale.ROOT, "%s×", value == (int) value ? Integer.toString((int) value) : Float.toString(value)); }
    private void renderStatus() {
        updateFullscreen();
        if (!BuildConfig.IS_TV && prepared && !completed && !buffering && qualitySwitch == null) {
            status.setText("");
            return;
        }
        String size = videoWidth > 0 && videoHeight > 0 ? "实际 " + videoWidth + "×" + videoHeight : "实际分辨率待解码确认";
        status.setText(currentKind + " · 当前 " + qualityLabel(actualQuality) + (quality.equals(actualQuality) ? "" : "（首选 " + qualityLabel(quality) + "）") + " · " + size);
    }
    private void selectSpeed() {
        if (!prepared || mediaPlayer == null) return;
        String[] labels = new String[PlayerOptions.SPEEDS.length]; int selected = -1;
        for (int i = 0; i < labels.length; i++) { labels[i] = rateLabel(PlayerOptions.SPEEDS[i]); if (Math.abs(actualSpeed - PlayerOptions.SPEEDS[i]) < .001f) selected = i; }
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("播放倍速 · 设备实际确认").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss(); if (!prepared || mediaPlayer == null) return;
            float before = mediaPlayer.observedSpeed(); boolean wasPlaying = video.isPlaying();
            try {
                float accepted = mediaPlayer.setVerifiedSpeed(PlayerOptions.SPEEDS[which], wasPlaying);
                try { PlayerOptions.update(this, new JSONObject().put("speed", (double) accepted)); }
                catch (Exception error) { if (Float.isFinite(before)) mediaPlayer.setVerifiedSpeed(before, wasPlaying); throw error; }
                requestedSpeed = accepted; actualSpeed = accepted;
            } catch (Exception error) { actualSpeed = mediaPlayer.observedSpeed(); Toast.makeText(this, "倍速设置未成功：" + error.getMessage(), Toast.LENGTH_LONG).show(); }
            updateButtons();
        }).setNegativeButton("关闭", null).create());
    }
    private static final class QualitySwitch {
        final int generation, serial; int ceiling, observedShortEdge; final String quality; final JSONObject episode; final PlaybackCache.QueueWindow window;
        Future<?> work; PlaybackVideoView view; PlaybackEngine player; Runnable timeout; PreparedMedia media;
        QualitySwitch(int generation, int serial, String quality, JSONObject episode, PlaybackCache.QueueWindow window) { this.generation = generation; this.serial = serial; this.quality = quality; this.episode = episode; this.window = window; }
    }
    private boolean validSwitch(QualitySwitch job) { return !destroyed && qualitySwitch == job && job.generation == generation && job.serial == qualitySerial; }
    private void releaseSwitch(QualitySwitch job) {
        if (job.timeout != null) main.removeCallbacks(job.timeout);
        if (job.work != null) job.work.cancel(true);
        if (job.player != null) job.player.setOnSeekCompleteListener(null);
        if (job.view != null && job.view != video) { job.view.stopPlayback(); playerRoot.removeView(job.view); }
        releasePrepared(job.media);
    }
    private void cancelQualitySwitch() {
        QualitySwitch job = qualitySwitch; qualitySwitch = null; ++qualitySerial;
        if (job != null) releaseSwitch(job);
    }
    private void failQualitySwitch(QualitySwitch job, String reason) {
        if (!validSwitch(job)) return;
        qualitySwitch = null; releaseSwitch(job); renderStatus(); updateButtons(); showControls();
        Toast.makeText(this, "保留当前播放；" + qualityLabel(job.quality) + " 切换未成功：" + (reason == null ? "对应片源暂未取得" : reason), Toast.LENGTH_LONG).show();
    }
    private void selectQuality() {
        if (!prepared || offline) return;
        String[] values = { "auto", "720p", "1080p", "2160p" };
        String[] labels = { "自动 · 可用片源", "720p · 实际短边至少 720", "1080p · 实际短边至少 1080", "4K · 片源和设备须支持" };
        int selected = Arrays.asList(values).indexOf(quality);
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("清晰度 · 先准备，成功后切换").setSingleChoiceItems(labels, selected, (dialog, which) -> {
            dialog.dismiss(); if (!values[which].equals(quality) || !values[which].equals(actualQuality) || qualitySwitch != null) switchQuality(values[which]);
        }).setNegativeButton("关闭", null).create());
    }
    private void switchQuality(String requested) {
        if (!prepared || offline || destroyed) return;
        cancelQualitySwitch(); cancelPlaybackWatchdogs(); if (requested.equals(quality) && requested.equals(actualQuality)) { renderStatus(); updateButtons(); return; }
        final QualitySwitch job = new QualitySwitch(generation, ++qualitySerial, requested, episode, localWindow); qualitySwitch = job;
        status.setText(currentKind + " · 后台准备 " + qualityLabel(requested) + "；当前播放保持"); updateButtons();
        resolveQualitySwitch(job, requested);
    }
    private void resolveQualitySwitch(QualitySwitch job, String startQuality) {
        if (!validSwitch(job)) return;
        try { job.work = resolver.submit(() -> {
            try {
                AdaptiveQuality.Result<PreparedMedia> result = AdaptiveQuality.prepare(startQuality,
                    stageQuality -> prepareMedia(job.episode, stageQuality, null, !stageQuality.equals(job.quality) || job.ceiling > 0, false, false, stageQuality.equals(job.quality) ? job.ceiling : stageCeiling(stageQuality, job.ceiling > 0 ? job.ceiling : AdaptiveQuality.ceiling(stageQuality)), job.window, job.generation, false),
                    error -> !"2160p".equals(job.quality) && PlaybackSources.canRecoverPlayback(error), () -> validSwitch(job),
                    (stageQuality, stage, error) -> main.post(() -> { if (validSwitch(job)) { status.setText((stage == 1 && startQuality.equals(job.quality) ? "后台准备 " : "当前档多次失败 · 自动降低至 ") + qualityLabel(stageQuality) + " 视频…；当前播放保持"); showControls(); } }), this::releasePrepared);
                PreparedMedia media = result.value;
                main.post(() -> {
                    if (!validSwitch(job)) { releasePrepared(media); return; }
                    job.media = media;
                    prepareQualitySwitch(job, media.uri);
                });
            } catch (Exception error) { main.post(() -> failQualitySwitch(job, error.getMessage())); }
        }); } catch (RejectedExecutionException error) { failQualitySwitch(job, "播放页面已关闭"); }
    }
    private void prepareQualitySwitch(QualitySwitch job, Uri uri) {
        if (!validSwitch(job)) return;
        // The current decoder stays above the candidate until prepare/seek/rate all succeed.
        final PreparedMedia stageMedia = job.media;
        PlaybackVideoView candidate = new PlaybackVideoView(this); job.view = candidate; candidate.setFocusable(true); candidate.setFocusableInTouchMode(true); installVideoTouch(candidate);
        playerRoot.addView(candidate, 0, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        job.timeout = () -> { if (!validCandidate(job, candidate, stageMedia)) return; if (!lowerQualitySwitch(job, "设备准备或定位超时")) failQualitySwitch(job, "设备准备或定位超时"); }; main.postDelayed(job.timeout, 20000);
        candidate.setOnErrorListener((mp, what, extra) -> { if (!validCandidate(job, candidate, stageMedia)) return true; String reason = "设备解码暂不可用（" + what + "/" + extra + "）"; if (!lowerQualitySwitch(job, reason)) failQualitySwitch(job, reason); return true; });
        candidate.setOnPreparedListener(mp -> {
            if (!validCandidate(job, candidate, stageMedia)) return;
            try {
                // Some decoders start as a side effect of setPlaybackParams. The candidate
                // must stay silent until the old player stops and the preference commit succeeds.
                mp.setVolume(0f, 0f);
                job.observedShortEdge = mp.getVideoWidth() > 0 && mp.getVideoHeight() > 0 ? Math.min(mp.getVideoWidth(), mp.getVideoHeight()) : 0;
                if (!MediaQuality.meets(mp.getVideoWidth(), mp.getVideoHeight(), job.media.quality)
                        || job.media.ceiling > 0 && (job.observedShortEdge <= 0 || job.observedShortEdge > job.media.ceiling)) {
                    String reason = "实际分辨率 " + mp.getVideoWidth() + "×" + mp.getVideoHeight() + " 未达到当前清晰度";
                    if (!lowerQualitySwitch(job, reason)) failQualitySwitch(job, reason); return;
                }
                job.player = mp;
                if (Float.isFinite(actualSpeed)) mp.setVerifiedSpeed(actualSpeed, false); else if (mp.isPlaying()) mp.pause();
                int position = completed ? 0 : Math.max(0, video.getCurrentPosition());
                if (candidate.getDuration() > 0) position = Math.min(position, Math.max(0, candidate.getDuration() - 250));
                if (position > 0) { mp.setOnSeekCompleteListener(ignored -> { if (!validCandidate(job, candidate, stageMedia) || job.player != mp) return; mp.setOnSeekCompleteListener(null); finishQualitySwitch(job); }); candidate.seekTo(position); }
                else finishQualitySwitch(job);
            } catch (Exception error) { failQualitySwitch(job, error.getMessage()); }
        });
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", job.media.plan == null ? NetworkGuard.UA : job.media.plan.userAgent);
        headers.put("Referer", job.media.plan == null ? NetworkGuard.REFERER : job.media.plan.referer);
        candidate.configure(video.software());
        candidate.setVideoURI(uri, headers);
    }
    private boolean validCandidate(QualitySwitch job, PlaybackVideoView candidate, PreparedMedia stageMedia) {
        return validSwitch(job) && job.view == candidate && job.media == stageMedia;
    }
    private boolean lowerQualitySwitch(QualitySwitch job, String reason) {
        if (!validSwitch(job) || job.media == null || "2160p".equals(job.quality)) return false;
        String lower = AdaptiveQuality.next(job.media.quality, job.observedShortEdge); if (lower == null) return false;
        int lowerCeiling = AdaptiveQuality.ceiling(lower);
        if (job.observedShortEdge > 0) lowerCeiling = Math.min(lowerCeiling, job.observedShortEdge - 1);
        job.ceiling = job.ceiling <= 0 ? lowerCeiling : Math.min(job.ceiling, lowerCeiling);
        job.observedShortEdge = 0;
        try { cache.invalidate(job.media.seriesId, job.episode.optString("vid"), job.episode.optInt("index"), job.media.quality, job.media.ceiling); }
        catch (Exception error) { return false; }
        if (job.timeout != null) main.removeCallbacks(job.timeout);
        if (job.player != null) job.player.setOnSeekCompleteListener(null);
        if (job.view != null && job.view != video) {
            PlaybackVideoView rejected = job.view; job.view = null; rejected.setOnErrorListener(null); rejected.setOnPreparedListener(null);
            rejected.stopPlayback(); playerRoot.removeView(rejected);
        }
        releasePrepared(job.media); job.media = null; job.player = null;
        status.setText(reason + " · 自动降低至 " + qualityLabel(lower) + "；当前播放保持");
        resolveQualitySwitch(job, lower); return true;
    }
    private void finishQualitySwitch(QualitySwitch job) {
        if (!validSwitch(job)) return;
        try {
            boolean shouldPlay = active && !userPaused && !completed;
            float accepted = Float.isFinite(actualSpeed) ? job.player.setVerifiedSpeed(actualSpeed, shouldPlay) : job.player.observedSpeed();
            if (shouldPlay) job.view.start(); else job.view.pause();
            PlayerOptions.update(this, new JSONObject().put("quality", job.quality));
            PlaybackVideoView old = video; old.stopPlayback(); adoptMedia(job.media); playerRoot.removeView(old);
            video = job.view; mediaPlayer = job.player; quality = job.quality;
            try {
                mediaPlayer.setVolume(1f, 1f);
                videoWidth = mediaPlayer.getVideoWidth(); videoHeight = mediaPlayer.getVideoHeight();
            } catch (IllegalStateException released) { mediaPlayer = null; }
            qualitySwitch = null; if (job.timeout != null) main.removeCallbacks(job.timeout);
            automaticCeiling = job.media.ceiling;
            actualSpeed = accepted; if (Float.isFinite(accepted)) requestedSpeed = accepted;
            bindPlayback(video, generation, episode);
            final PlaybackVideoView currentView = video; final int token = generation;
            watchProgressSeeks(mediaPlayer, token, currentView);
            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> { if (video == currentView && token == generation) { videoWidth = width; videoHeight = height; renderStatus(); } });
            currentManual = false; currentCache = job.media.cached; currentWeb = false;
            currentKind = "清晰度已切换 · " + job.media.kind; renderStatus(); updateButtons(); showControls(); pruneCache();
            if (autoplay) schedulePrefetch(generation);
        } catch (Exception error) { failQualitySwitch(job, error.getMessage()); }
    }
    private Uri downloaded(JSONObject target) throws Exception {
        return LocalDownloadLookup.find(this, owner(target).optString("seriesId"), target.optString("vid"));
    }
    private void playbackError(int token, String message) {
        if (destroyed || token != generation || isFinishing()) return;
        cancelQualitySwitch(); cancelPlaybackWatchdogs(); decoderPreparing = false;
        prepared = false; video.stopPlayback(); status.setText(message == null ? "本集片源暂不可用" : message); updateButtons(); showControls();
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("第 " + episode.optInt("index") + " 集暂未播放")
            .setMessage(status.getText()).setPositiveButton("重试", (dialog, which) -> loadAt(current, null, resumeAt))
            .setNeutralButton("内置软解", (dialog, which) -> launchSoftDecoder(token, adoptedMedia)).setNegativeButton("返回", (dialog, which) -> exitPlayer())
            .setOnCancelListener(dialog -> exitPlayer()).create());
    }
    /** Falls back to the built-in software decoder (tvNative ships libVLC) when the device cannot
     *  decode the source itself, e.g. H.265 on phones and TVs without hardware HEVC. Returns false
     *  when the layer is unavailable so the regular error flow continues. */
    private boolean launchSoftDecoder(int token, PreparedMedia media) {
        if (softActive || destroyed || token != generation || isFinishing()) return false;
        if (!SoftDecoder.available() || media == null || media.uri == null) return false;
        PlaybackSources.Plan plan = media.plan;
        String userAgent = plan == null ? NetworkGuard.UA : plan.userAgent, referer = plan == null ? NetworkGuard.REFERER : plan.referer;
        String caption = series.optString("title", "红果短剧") + " · 第 " + episode.optInt("index") + " 集";
        Object layer = SoftDecoder.start(softHost, this, media.uri, userAgent, referer, Math.max(0, lastGoodPosition), caption);
        if (layer == null) return false;
        softLayer = layer; softActive = true; softToken = token; adoptedMedia = media;
        try { video.stopPlayback(); } catch (RuntimeException ignored) { }
        releaseCurrent(); mediaPlayer = null; decoderPreparing = false; cancelPlaybackWatchdogs();
        currentKind = "内置软解播放中"; renderStatus(); showControls(); updateButtons();
        return true;
    }
    private String decoderMode() {
        try { return PlayerOptions.decoder(PlayerOptions.read(this).optString("decoder", "auto")); }
        catch (Exception error) { return "auto"; }
    }
    private final SoftDecoder.Host softHost = new SoftDecoder.Host() {
        @Override public JSONObject series() { return PlayerActivity.this.series; }
        @Override public JSONObject episode() { return PlayerActivity.this.episode; }
        @Override public void onSoftEnded() {
            softActive = false; SoftDecoder.stop(softLayer); softLayer = null;
            completed = true;
            recordProgress(PlayerActivity.this.series, PlayerActivity.this.episode, 0);
            if (autoplay && current + 1 < queue.size()) loadAt(current + 1, null, 0);
            else { currentKind = current + 1 == queue.size() ? "本剧已播完" : "本集已播完 · " + (BuildConfig.IS_TV ? "按下一集" : "上滑切集"); renderStatus(); showControls(); updateButtons(); }
        }
        @Override public void onSoftClosed() {
            softActive = false; softLayer = null;
            if (!destroyed) { renderStatus(); showControls(); updateButtons(); }
        }
        @Override public void onSoftProgress(long positionMs, long durationMs) {
            recordProgress(PlayerActivity.this.series, PlayerActivity.this.episode, Math.max(0, positionMs));
        }
        @Override public void onSoftFailed(String message) {
            softActive = false; SoftDecoder.stop(softLayer); softLayer = null;
            if (destroyed) return;
            main.post(() -> { if (!destroyed && !softActive) playbackError(softToken, message + "；原错误：" + lastMediaError); });
        }
    };
    private void showPicture() {
        if (casting() || castWasActive) {
            Toast.makeText(this, "结束投屏后可调节本机画面", Toast.LENGTH_SHORT).show();
            return;
        }
        var before = PlaybackPicture.read(this);
        String decoder = decoderMode();
        showPlayerDialog(PlaybackPictureDialog.create(this, before, decoder, (picture, mode) -> {
            try {
                if (before.equals(picture) && decoder.equals(mode)) return;
                picture.save(this);
                try { PlayerOptions.update(this, new JSONObject().put("decoder", mode)); }
                catch (Exception failure) { before.save(this); throw failure; }
                int position = prepared ? Math.max(lastGoodPosition, video.getCurrentPosition()) : resumeAt;
                loadAt(current, null, position, userPaused || completed);
            } catch (Exception failure) {
                Toast.makeText(this, "画面与解码设置未保存，当前播放保持", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void applyDanmakuAppearance() {
        var preferences = getSharedPreferences("danmaku_appearance_v1", MODE_PRIVATE);
        danmakuOverlay.appearance(preferences.getInt("density", 1), preferences.getInt("edge", 0),
            preferences.getBoolean("avoidSubtitles", true));
    }

    private void showDanmakuAppearance() {
        var preferences = getSharedPreferences("danmaku_appearance_v1", MODE_PRIVATE);
        String[] choices = {"低密度", "标准密度", "高密度", "阴影", "描边", "纯色文字",
            preferences.getBoolean("avoidSubtitles", true) ? "关闭字幕避让" : "开启字幕避让"};
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("弹幕外观").setItems(choices, (dialog, index) -> {
            var edit = preferences.edit();
            if (index < 3) edit.putInt("density", index);
            else if (index < 6) edit.putInt("edge", index - 3);
            else edit.putBoolean("avoidSubtitles", !preferences.getBoolean("avoidSubtitles", true));
            edit.apply(); applyDanmakuAppearance();
        }).setNegativeButton("取消", null).create());
    }

    private void updateDanmakuButton() {
        if (danmakuButton == null) return;
        String value = !danmakuEnabled ? "关" : offline ? "离线停用" : casting() || castWasActive ? "投屏停用" : danmakuNotice.contains("未载入") ? "未载入" : danmakuNotice.contains("正在") ? "读取中" : danmakuNotice.contains("暂无") ? "本段无" : "开";
        if (BuildConfig.IS_TV) danmakuButton.setText("弹幕 " + value);
        else danmakuButton.setSelected("开".equals(value));
        danmakuButton.setContentDescription("时间轴弹幕：" + value + "；" + danmakuNotice + "；点击" + (danmakuEnabled ? "关闭" : "开启"));
    }
    private void toggleDanmaku() {
        try {
            danmakuEnabled = PlayerOptions.update(this, new JSONObject().put("danmaku", !danmakuEnabled)).getBoolean("danmaku");
            danmakuNotice = danmakuEnabled ? "正在读取弹幕…" : "弹幕已关闭";
            updateDanmaku(); updateDanmakuButton();
            Toast.makeText(this, danmakuEnabled ? offline ? "已下载播放不读取在线弹幕" : casting() || castWasActive ? "投屏不显示本机弹幕" : "已开启时间轴弹幕，暂停和拖动跟随视频" : "弹幕已关闭", Toast.LENGTH_SHORT).show();
        } catch (Exception error) { Toast.makeText(this, "弹幕设置保存未成功", Toast.LENGTH_LONG).show(); }
    }
    private void updateDanmaku() {
        if (danmaku == null || video == null || series == null || episode == null) return;
        try {
            long position = prepared ? Math.max(0, video.getCurrentPosition()) : Math.max(0, resumeAt);
            String sid = series.optString("seriesId");
            boolean local = active && prepared && !offline && !casting() && !castWasActive && !PlaybackSources.isRetiredSeries(sid);
            danmaku.update(sid, episode.optString("vid"), generation, position,
                Math.max(1, video.getDuration()), local, danmakuEnabled); updateDanmakuButton();
        } catch (RuntimeException ignored) { danmaku.suspend(); } // A decoder transition never turns a comment error into playback failure.
    }
    private void move(int delta) { if (current + delta >= 0 && current + delta < queue.size()) loadAt(current + delta, null, 0); }
    private void selectEpisode() {
        if (!BuildConfig.IS_TV) {
            if (current < 0 || current >= queue.size()) return;
            if (recommendationPrefetch != null) recommendationPrefetch.ignoreSelection();
            showPlayerDialog(EpisodePicker.create(this, queue, current, index -> loadAt(index, null, 0)));
            return;
        }
        String[] labels = new String[queue.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = owner(queue.get(i)).optString("title") + " · 第 " + queue.get(i).optInt("index") + " 集" + (i == current ? " · 当前" : "");
        showPlayerDialog(new AlertDialog.Builder(this).setTitle(offline ? "已下载分集" : "选集 · 逐集核验片源").setSingleChoiceItems(labels, current, (dialog, which) -> {
            dialog.dismiss(); loadAt(which, null, 0);
        }).setNegativeButton("关闭", null).create());
    }
    private void togglePlayback() {
        if (castCommand("toggle", 0)) return;
        if (!prepared) return;
        if (video.isPlaying()) { video.pause(); userPaused = true; saveProgress(); }
        else { if (completed) { video.seekTo(0); completed = false; } video.start(); userPaused = false; }
        updateButtons();
    }
    private void seekBy(int delta) {
        if (castCommand("seekRelative", delta)) { showControls(); return; }
        if (!prepared) return;
        video.seekTo(Math.max(0, Math.min(Math.max(0, video.getDuration() - 250), video.getCurrentPosition() + delta))); showControls();
    }
    private void pruneCache() {
        if (!cleanup || offline || destroyed || castWasActive) return;
        try { cacheWorker.execute(() -> {
            if (destroyed || castWasActive) return;
            // Read the current queue position at execution time, never a stale prefetch index.
            PlaybackCache.QueueWindow window = localWindow; if (window != null) { window.moveTo(current); window.prune(); }
        }); } catch (RejectedExecutionException ignored) { }
    }
    private void schedulePrefetch(int token) {
        int nextIndex = current + 1; if (nextIndex >= queue.size() || destroyed || castWasActive) return;
        final String targetQuality = actualQuality;
        final int targetCeiling = stageCeiling(targetQuality, automaticCeiling);
        JSONObject following = queue.get(nextIndex); final String sid = owner(following).optString("seriesId");
        if (prefetch != null) prefetch.cancel(true);
        prefetchedIndex = nextIndex;
        try { prefetch = cacheWorker.submit(() -> {
            try {
                if (token != generation || destroyed || !autoplay || castWasActive) return;
                if (cache.find(sid, following.optString("vid"), following.optInt("index"), targetQuality, targetCeiling) != null
                        || (targetCeiling == 0 && "auto".equals(targetQuality) && downloaded(following) != null)) return;
                PlaybackSources.Plan plan = PlaybackSources.resolvePlayback(sid, following.optString("vid"), targetQuality, targetCeiling, false, false, RegisteredPlaybackLookup.SPECULATIVE);
                String path = Uri.parse(plan.url).getPath();
                if (path != null && !path.toLowerCase(Locale.ROOT).endsWith(".m3u8")
                        && token == generation && !destroyed && !castWasActive && autoplay && actualQuality.equals(targetQuality)
                        && stageCeiling(actualQuality, automaticCeiling) == targetCeiling)
                    cache.fetch(sid, following.optString("vid"), following.optInt("index"), plan, targetQuality);
            } catch (Exception ignored) { /* The next episode is checked again when requested. */ }
            finally { main.post(this::pruneCache); }
        }); } catch (RejectedExecutionException ignored) { }
    }
    private static String format(int millis) { int seconds = Math.max(0, millis / 1000); return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60); }
    private final Runnable tick = new Runnable() { public void run() {
        if (destroyed) return;
        updateDanmaku();
        if (casting()) { cancelPlaybackWatchdogs(); decoderPreparing = false; renderCastControls(); main.postDelayed(this, 500); return; }
        updateBufferingWatchdog(generation, video);
        if (prepared && !dragging) {
            lastGoodPosition = Math.max(0, video.getCurrentPosition());
            int duration = video.getDuration();
            if (!completed && active && video.isPlaying() && cuts.finishesAt(lastGoodPosition, duration)) {
                video.pause(); finishEpisode(duration); main.postDelayed(this, 500); return;
            }
            seek.setMax(Math.max(1, video.getDuration())); seek.setProgress(lastGoodPosition); time.setText(format(video.getCurrentPosition()) + " / " + format(video.getDuration()));
            if (mediaPlayer != null) {
                // 换集/换源窗口内实例可能已释放：捕获状态异常并放弃失效引用，避免轮询崩溃。
                try {
                    if (video.isPlaying()) actualSpeed = mediaPlayer.observedSpeed();
                    int width = mediaPlayer.getVideoWidth(), height = mediaPlayer.getVideoHeight();
                    if (width != videoWidth || height != videoHeight) { videoWidth = width; videoHeight = height; renderStatus(); }
                } catch (IllegalStateException released) { mediaPlayer = null; }
            }
            periodicProgress(); updateButtons();
        }
        main.postDelayed(this, 500);
    }};
    private boolean casting() {
        return !BuildConfig.IS_TV && (browserCastSelected ? browserCastService != null : castService != null) && castSnapshot != null && castSnapshot.active
            && !queueIdentity.isEmpty() && queueIdentity.equals(castSnapshot.seriesId);
    }
    private int playbackDuration() { return casting() ? (int) Math.min(Integer.MAX_VALUE, castSnapshot.durationMs) : video.getDuration(); }
    private boolean castCommand(String command, long value) {
        if (!casting()) return false;
        if (browserCastSelected) return browserCastCommand(command, value);
        try {
            switch (command) {
                case "select": castService.select((int) value); break;
                case "toggle": if (castSnapshot.playing) castService.pause(); else castService.play(); break;
                case "play": castService.play(); break;
                case "pause": castService.pause(); break;
                case "seek": castService.seek(Math.max(0, value)); break;
                case "seekRelative": castService.seek(Math.max(0, castSnapshot.positionMs + value)); break;
                case "autoplay": castService.setAutoplay(value != 0); break;
                default: return false;
            }
        } catch (RuntimeException error) { Toast.makeText(this, "投屏操作未完成，请重试", Toast.LENGTH_LONG).show(); }
        return true;
    }
    private boolean browserCastCommand(String command, long value) {
        if (browserCastService == null) return false;
        switch (command) {
            case "select": browserCastService.select((int) value); break;
            case "toggle": if (castSnapshot.playing) browserCastService.pause(); else browserCastService.play(); break;
            case "play": browserCastService.play(); break;
            case "pause": browserCastService.pause(); break;
            case "seek": browserCastService.seek(Math.max(0, value)); break;
            case "seekRelative": browserCastService.seek(Math.max(0, castSnapshot.positionMs + value)); break;
            case "autoplay": browserCastService.setAutoplay(value != 0); break;
            default: return false;
        }
        return true;
    }
    private void onProtocolCastSnapshot(boolean browser, DlnaSession.Snapshot value) {
        if (destroyed || isFinishing()) return;
        // Idle callbacks from the other bound service must never clear an active receiver.
        if (browser) {
            if (!value.active && !browserCastSelected) {
                if (showBrowserPairWhenReady && value.error != null && !value.error.isEmpty()) {
                    showBrowserPairWhenReady = false; notifyCastError(value, true);
                }
                return;
            }
            if (value.active && castService != null && castService.snapshot().active) return;
            browserCastSelected = true;
        } else {
            if (browserCastSelected && browserCastService != null && browserCastService.snapshot().active) return;
            browserCastSelected = false;
        }
        onCastSnapshot(value);
        if (browser && value.active && showBrowserPairWhenReady && browserCastService != null && !browserCastService.landingUrl().isEmpty()) {
            showBrowserPairWhenReady = false; showBrowserPairing();
        }
        if (browser && !value.active) { browserCastSelected = false; showBrowserPairWhenReady = false; }
    }
    private void onCastSnapshot(DlnaSession.Snapshot value) {
        if (destroyed || isFinishing()) return;
        boolean before = castWasActive;
        castSnapshot = value;
        boolean owns = casting();
        if (owns && !before) {
            saveProgressBeforeCast();
            cancelQualitySwitch(); cancelPlaybackWatchdogs(); decoderPreparing = false; ++generation;
            if (resolution != null) resolution.cancel(true);
            video.stopPlayback(); mediaPlayer = null; discardPendingMedia(); releaseCurrent(); closeLocalWindow();
            prepared = false; completed = false; buffering = false;
        }
        castWasActive = owns;
        notifyCastError(value, owns || before);
        if (owns) {
            if (value.current >= 0 && value.current < queue.size()) { current = value.current; episode = queue.get(current); series = owner(episode); }
            actualQuality = value.actualQuality;
            autoplay = value.autoplay;
            quality = value.preferredQuality;
            if (!value.preparing || value.durationMs > 0 || value.positionMs > 0) {
                resumeAt = (int) Math.min(Integer.MAX_VALUE, Math.max(0, value.positionMs)); lastGoodPosition = resumeAt;
            }
            title.setText(series.optString("title") + " · 第 " + episode.optInt("index") + " 集");
            renderCastControls(); showControls();
        } else if (before) {
            // A stopped cast returns at the last receiver-confirmed position, not local 00:00.
            prepared = false;
            loadAtInternal(current, null, Math.max(0, resumeAt), false, false, false);
            if (castButton != null) castButton.setSelected(false);
        }
    }
    private void saveProgressBeforeCast() {
        if (prepared && episode != null) {
            try { int position = Math.max(0, video.getCurrentPosition()); resumeAt = position; lastGoodPosition = position; recordProgress(owner(episode), episode, position); }
            catch (RuntimeException ignored) { }
        }
    }
    private boolean renderCastControls() {
        if (!casting()) return false;
        DlnaSession.Snapshot snapshot = castSnapshot;
        prepared = snapshot.durationMs > 0 && !snapshot.preparing
            && (!browserCastSelected || "PLAYING".equals(snapshot.state) || "PAUSED".equals(snapshot.state) || "BUFFERING".equals(snapshot.state) || "ENDED".equals(snapshot.state));
        completed = "ENDED".equals(snapshot.state);
        qualityButton.setEnabled(false); qualityButton.setText(qualityLabel(snapshot.actualQuality));
        speedButton.setEnabled(false); speedButton.setText("—×");
        speedButton.setContentDescription("倍速由投屏接收端控制");
        if (castButton != null) { castButton.setSelected(true); castButton.setContentDescription("投屏到 " + castDiagnosticValue(snapshot.deviceName) + "，点击管理或断开"); }
        if (!dragging) {
            int position = (int) Math.min(Integer.MAX_VALUE, Math.max(0, snapshot.positionMs));
            int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0, snapshot.durationMs));
            seek.setMax(Math.max(1, duration)); seek.setProgress(position); seek.setEnabled(duration > 0 && !snapshot.preparing && (!browserCastSelected || prepared));
            time.setText(format(position) + " / " + (duration > 0 ? format(duration) : "--:--"));
        }
        String detail = browserCastSelected ? browserCastStatusDetail(snapshot) : castStatusDetail(snapshot);
        if (browserCastSelected && snapshot.targetCurrent != snapshot.current && snapshot.targetCurrent >= 0 && snapshot.targetCurrent < queue.size())
            detail += " · 正在切到第 " + queue.get(snapshot.targetCurrent).optInt("index") + " 集";
        status.setText((browserCastSelected ? "浏览器 · " : "DLNA · ") + castDiagnosticValue(snapshot.deviceName) + " · " + detail + " · 当前 " + qualityLabel(snapshot.actualQuality)
            + (snapshot.error == null || snapshot.error.isEmpty() ? "" : "\n" + castDiagnosticValue(snapshot.error)));
        updateControlsState(); return true;
    }
    private static String browserCastStatusDetail(DlnaSession.Snapshot snapshot) {
        switch (snapshot.state) {
            case "CONNECTING": return "正在启动接收页";
            case "WAITING_RECEIVER": return "请在接收端打开网页并输入配对码";
            case "PREPARING": return "正在准备片源";
            case "WAITING_GESTURE": return "请在接收页面点按开始播放";
            case "WAITING_RECEIVER_PLAY": return "等待接收端确认播放";
            case "BUFFERING": return "接收端正在缓冲";
            case "DISCONNECTED": return "接收页面连接中断，未自动跳集";
            case "ERROR": return "接收端播放未完成，请查看诊断";
            case "ENDED": return "本集已播完";
            case "PLAYING": return "接收端正在播放";
            case "PAUSED": return "接收端已暂停";
            default: return "等待接收端";
        }
    }
    private static String castStatusDetail(DlnaSession.Snapshot snapshot) {
        // A pending file task must not hide a lost receiver connection or an unconfirmed handoff.
        if ("DISCONNECTED".equals(snapshot.state)) return "电视连接已断开，请重试当前集或重新选择电视";
        if ("ERROR".equals(snapshot.state)) return "电视播放尚未确认，请查看投屏诊断";
        if ("CONNECTING".equals(snapshot.state)) return "正在连接电视";
        if ("WAITING_TV".equals(snapshot.state)) return "片源已发送，等待电视确认播放";
        if ("PREPARING".equals(snapshot.state) || snapshot.preparing) return "正在准备电视片源";
        if ("ENDED".equals(snapshot.state)) return "本集已播完";
        if ("VERIFYING_END".equals(snapshot.state)) return "正在确认本集结束";
        return snapshot.playing ? "电视正在播放" : "投屏状态：" + castDiagnosticValue(snapshot.state);
    }
    private void notifyCastError(DlnaSession.Snapshot snapshot, boolean relevant) {
        if (!relevant) return;
        String message = castDiagnosticValue(snapshot.error);
        if (message.isEmpty()) {
            // Only confirmed playback clears error de-duplication, not every preparation poll.
            if (snapshot.active && !snapshot.preparing && "PLAYING".equals(snapshot.state)) lastCastError = "";
        } else if (!message.equals(lastCastError)) {
            lastCastError = message;
            Toast.makeText(this, "投屏：" + message, Toast.LENGTH_LONG).show();
        }
    }
    private static String castDiagnosticValue(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("(?i)\\b(?:(?:https?|rtsp)://|www\\.)\\S+", "[地址已隐藏]")
            .replaceAll("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}]", " ").trim();
        return clean.length() <= 240 ? clean : clean.substring(0, 240) + "…";
    }
    private String castDiagnostics() {
        DlnaSession.Snapshot snapshot = castSnapshot;
        if (snapshot == null) return "版本：" + castDiagnosticValue(BuildConfig.VERSION_NAME) + "\n投屏会话：尚未连接";
        String transport = "控制/供流：服务尚未连接";
        if (browserCastSelected && browserCastService != null) transport = browserCastService.diagnostics();
        else if (castService != null) try { transport = castService.diagnostics(); }
        catch (RuntimeException ignored) { transport = "控制/供流诊断暂未就绪"; }
        StringBuilder cleanTransport = new StringBuilder();
        String[] lines = transport == null ? new String[0] : transport.split("\n", -1);
        for (int i = 0; i < Math.min(12, lines.length); i++) cleanTransport.append('\n').append(castDiagnosticValue(lines[i]));
        return "版本：" + castDiagnosticValue(BuildConfig.VERSION_NAME)
            + "\n设备：" + castDiagnosticValue(snapshot.deviceName)
            + "\nstate：" + castDiagnosticValue(snapshot.state)
            + "\ncurrent/target（从 0 开始）：" + snapshot.current + "/" + snapshot.targetCurrent
            + "\npreparing：" + snapshot.preparing
            + "\nposition/duration（毫秒）：" + Math.max(0, snapshot.positionMs) + "/" + Math.max(0, snapshot.durationMs)
            + "\n实际/偏好画质：" + castDiagnosticValue(snapshot.actualQuality) + "/" + castDiagnosticValue(snapshot.preferredQuality)
            + "\n错误：" + castDiagnosticValue(snapshot.error)
            + cleanTransport
            + "\n说明：集数位置与命令受理不代表电视已出画；写出字节不代表解码成功。";
    }
    private void showCastDiagnostics() {
        final String detail = castDiagnostics();
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("投屏诊断").setMessage(detail)
            .setPositiveButton("复制诊断", (dialog, which) -> copyCastDiagnostics(detail))
            .setNegativeButton("关闭", null).create());
    }
    private void copyCastDiagnostics(String detail) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("红果投屏诊断", detail));
            Toast.makeText(this, "投屏诊断已复制", Toast.LENGTH_LONG).show();
        } else Toast.makeText(this, "剪贴板尚未就绪，请重试", Toast.LENGTH_LONG).show();
    }
    private int castMergeWindow() { return Math.max(1, getSharedPreferences("hongguo.cast", MODE_PRIVATE).getInt("mergeWindow", 1)); }
    private void setCastMergeWindow(int value) {
        getSharedPreferences("hongguo.cast", MODE_PRIVATE).edit().putInt("mergeWindow", Math.max(1, value)).apply();
        Toast.makeText(this, "合并投屏已" + (value > 1 ? "设为每段 " + value + " 集；重新连接投屏后生效" : "关闭；重新连接投屏后生效"), Toast.LENGTH_LONG).show();
    }
    private void showMergeCastDialog() {
        int current = castMergeWindow();
        int[] options = {1, 2, 3, 5};
        String[] labels = {"关闭（每集单独投屏）", "每段 2 集", "每段 3 集", "每段 5 集"};
        int selected = 0;
        for (int i = 0; i < options.length; i++) if (options[i] == current) selected = i;
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("合并投屏")
            .setMessage("投屏时把连续几集合并为一个视频交给电视，减少电视端换集次数。仅合并编码参数一致的分集，不一致时自动回退单集；合并会额外占用临时缓存空间，投屏结束后自动清理。")
            .setSingleChoiceItems(labels, selected, (dialog, which) -> { setCastMergeWindow(options[which]); dialog.dismiss(); })
            .setNegativeButton("取消", null).create());
    }
    private void showCastChoices() {
        if (BuildConfig.IS_TV || destroyed) return;
        if (offline) { Toast.makeText(this, "请从在线剧集进入投屏；手动下载保持本地播放", Toast.LENGTH_LONG).show(); return; }
        boolean hasBrowser = browserCastService != null && browserCastService.snapshot().active;
        boolean hasDlna = castService != null && castService.snapshot().active;
        if ((hasBrowser || hasDlna) && !casting()) {
            showPlayerDialog(new AlertDialog.Builder(this).setTitle("另一部剧正在投屏")
                .setMessage("先停止原投屏，再从本剧选择接收设备。")
                .setPositiveButton("停止原投屏", (dialog, which) -> { if (hasBrowser) browserCastService.stopCast(); if (hasDlna) castService.stopCast(); })
                .setNegativeButton("保留", null).create());
            return;
        }
        if (hasBrowser) { showBrowserCastMenu(); return; }
        if (hasDlna) { showCastDevices(); return; }
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("选择投屏方式")
            .setItems(new String[]{"电视／车机 · DLNA", "Mac／iPhone／iPad · 浏览器接收"}, (dialog, which) -> {
                if (which == 0) showCastDevices(); else startBrowserCast();
            }).setNeutralButton(castMergeWindow() > 1 ? "DLNA 合并投屏：每段 " + castMergeWindow() + " 集" : "DLNA 合并投屏：关闭", (dialog, which) -> showMergeCastDialog())
            .setNegativeButton("取消", null).create());
    }
    private void startBrowserCast() {
        if (destroyed || BuildConfig.IS_TV) return;
        if (browserCastService == null) { Toast.makeText(this, "接收服务正在准备，请稍后再试", Toast.LENGTH_LONG).show(); return; }
        if (castService != null && castService.snapshot().active) { Toast.makeText(this, "请先停止电视投屏再切换", Toast.LENGTH_LONG).show(); return; }
        try {
            String bind = castWifiAddress();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 914);
            JSONArray episodes = new JSONArray(); for (JSONObject row : queue) episodes.put(row);
            long position = prepared ? Math.max(0, video.getCurrentPosition()) : Math.max(0, resumeAt);
            showBrowserPairWhenReady = true;
            browserCastService.connect(bind, series, episodes, current, quality, autoplay, cleanup, position);
        } catch (Exception error) { showBrowserPairWhenReady = false; Toast.makeText(this, "请连接同一 Wi-Fi 后重试浏览器投屏", Toast.LENGTH_LONG).show(); }
    }
    private void showBrowserCastMenu() {
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("浏览器投屏")
            .setItems(new String[]{"查看接收地址／配对码", "重试当前集", "停止投屏，返回手机", "查看投屏诊断"}, (dialog, which) -> {
                if (browserCastService == null) return;
                if (which == 0) showBrowserPairing();
                else if (which == 1) browserCastService.select(castSnapshot.targetCurrent >= 0 ? castSnapshot.targetCurrent : current);
                else if (which == 2) browserCastService.stopCast();
                else showCastDiagnostics();
            }).setNegativeButton("关闭", null).create());
    }
    private void showBrowserPairing() {
        if (destroyed || browserCastService == null) return;
        String url = browserCastService.landingUrl(), code = browserCastService.pairCode();
        if (url.isEmpty()) { Toast.makeText(this, "接收页面正在启动", Toast.LENGTH_SHORT).show(); return; }
        String info = "同一 Wi-Fi 下，在 Mac／iPhone／iPad 的 Safari 打开：\n\n" + url
            + "\n\n配对码：" + code + "\n\n首次连接后，请在接收页点按开始播放。手机可切集、控制连播，连播开启时预缓存后 5 集。"
            + "\n\nApple TV：通过 Safari 的 AirPlay 按钮转投；不是安卓直接连接 AirPlay。配对码 10 分钟有效，过期后停止并重新开启。";
        showPlayerDialog(new AlertDialog.Builder(this).setTitle("苹果／浏览器接收")
            .setMessage(info).setPositiveButton("复制连接信息", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("红果接收连接", url + "\n配对码：" + code)); Toast.makeText(this, "连接信息已复制", Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("关闭", null).create());
    }
    private void showCastDevices() {
        if (BuildConfig.IS_TV || destroyed) return;
        if (offline) { Toast.makeText(this, "请从在线剧集进入投屏；手动下载保持本地播放", Toast.LENGTH_LONG).show(); return; }
        if (castService == null) { Toast.makeText(this, "投屏服务正在准备，请稍后再试", Toast.LENGTH_LONG).show(); return; }
        if (casting()) {
            showPlayerDialog(new AlertDialog.Builder(this).setTitle("投屏到 " + castDiagnosticValue(castSnapshot.deviceName))
                .setItems(new String[]{"选择其他电视", "重试当前集", "停止投屏，返回手机", "查看投屏诊断", "合并投屏：" + (castMergeWindow() > 1 ? "每段 " + castMergeWindow() + " 集" : "关闭")}, (dialog, which) -> {
                    if (which == 0) searchCastDevices();
                    else if (which == 1) castService.select(castSnapshot.targetCurrent >= 0 ? castSnapshot.targetCurrent : current);
                    else if (which == 2) castService.stopCast();
                    else if (which == 3) showCastDiagnostics();
                    else if (which == 4) showMergeCastDialog();
                }).setNegativeButton("关闭", null).create());
        } else searchCastDevices();
    }
    private void searchCastDevices() {
        cancelCastDiscovery();
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        castDiscoveryCancelled = cancelled;
        final ArrayList<DlnaDevice> devices = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        final TextView hint = label(14); hint.setPadding(dp(20), dp(12), dp(20), dp(12));
        hint.setText("正在搜索同一 Wi-Fi 下的 DLNA 电视…\n请打开电视的 DLNA／无线投屏接收页面。");
        final AlertDialog picker = new AlertDialog.Builder(this).setTitle("选择电视 · DLNA").setView(hint)
            .setAdapter(adapter, (dialog, which) -> { if (which >= 0 && which < devices.size()) connectCast(devices.get(which)); })
            .setNegativeButton("取消", null).create();
        ++hudDialogs; showControls();
        picker.setOnDismissListener(dialog -> { cancelled.set(true); if (castDiscovery != null) castDiscovery.cancel(true); hudDialogs = Math.max(0, hudDialogs - 1); showControls(); });
        picker.show();
        castDiscovery = castDiscoveryWorker.submit(() -> {
            android.net.wifi.WifiManager.MulticastLock multicast = null;
            try {
                android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi == null) throw new IllegalStateException("请连接 Wi-Fi 后重试");
                multicast = wifi.createMulticastLock("hongguo-dlna-discovery"); multicast.setReferenceCounted(false); multicast.acquire();
                DlnaDiscovery.discover(castWifiAddress(), 6000, device -> main.post(() -> {
                    if (destroyed || cancelled.get() || !picker.isShowing()) return;
                    devices.add(device); adapter.add(device.name + " · " + device.host); adapter.notifyDataSetChanged();
                }), cancelled::get);
                main.post(() -> { if (!destroyed && !cancelled.get() && picker.isShowing()) hint.setText(devices.isEmpty()
                    ? "没有找到 DLNA 电视。请检查同一 Wi-Fi、电视接收入口，以及路由器是否开启了设备隔离。关闭后可重新搜索。"
                    : "请选择电视；列表仅包含支持 DLNA 媒体播放的接收端。"); });
            } catch (Exception error) {
                main.post(() -> { if (!destroyed && !cancelled.get() && picker.isShowing()) hint.setText("搜索未完成，请检查 Wi-Fi 与电视投屏入口后重试。"); });
            } finally { if (multicast != null && multicast.isHeld()) multicast.release(); }
        });
    }
    private String castWifiAddress() throws java.io.IOException {
        android.net.ConnectivityManager manager = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager != null) for (android.net.Network network : manager.getAllNetworks()) {
            android.net.NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue;
            android.net.LinkProperties properties = manager.getLinkProperties(network);
            if (properties == null) continue;
            for (android.net.LinkAddress row : properties.getLinkAddresses()) {
                java.net.InetAddress address = row.getAddress();
                if (address instanceof java.net.Inet4Address && DlnaNetwork.isPrivateIpv4(address.getHostAddress())) return address.getHostAddress();
            }
        }
        throw new java.io.IOException("请连接有局域网 IPv4 地址的 Wi-Fi 后重试投屏");
    }
    private void connectCast(DlnaDevice device) {
        if (destroyed || castService == null) return;
        if (browserCastService != null && browserCastService.snapshot().active) { Toast.makeText(this, "请先停止浏览器投屏再切换", Toast.LENGTH_LONG).show(); return; }
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 913);
            JSONArray episodes = new JSONArray(); for (JSONObject row : queue) episodes.put(row);
            long position = casting() ? castSnapshot.positionMs : prepared ? Math.max(0, video.getCurrentPosition()) : Math.max(0, resumeAt);
            castService.connect(device, series, episodes, current, quality, autoplay, cleanup, position, castMergeWindow());
        } catch (Exception error) { Toast.makeText(this, "投屏连接未完成，请重新选择电视", Toast.LENGTH_LONG).show(); }
    }
    private void cancelCastDiscovery() {
        if (castDiscoveryCancelled != null) castDiscoveryCancelled.set(true);
        if (castDiscovery != null) castDiscovery.cancel(true);
        castDiscovery = null;
    }
    private void detachCast() {
        cancelCastDiscovery(); castDiscoveryWorker.shutdownNow();
        if (castService != null) castService.removeListener(castListener);
        if (castBound) { unbindService(castConnection); castBound = false; }
        castService = null; // Started foreground service and its file leases deliberately survive this Activity.
        if (browserCastService != null) browserCastService.removeListener(browserCastListener);
        if (browserCastBound) { unbindService(browserCastConnection); browserCastBound = false; }
        browserCastService = null;
    }
    private void exitPlayer() {
        if (surfaceTaps != null) surfaceTaps.cancel();
        if (controlsHud != null) controlsHud.suspend();
        saveProgress();
        if (prepared) handoffPlayback(true);
        finish();
    }
    @Override public void onBackPressed() {
        if (fullscreen) {
            toggleFullscreen();
            showControls();
        } else exitPlayer();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && systemBackCleanup == null) { if (event.getAction() == KeyEvent.ACTION_UP) onBackPressed(); return true; }
        int key = event.getKeyCode();
        boolean navigation = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        if (navigation && key == wakeKey) { showControls(); if (event.getAction() == KeyEvent.ACTION_UP) wakeKey = KeyEvent.KEYCODE_UNKNOWN; return true; }
        if (navigation && event.getAction() == KeyEvent.ACTION_DOWN && controlsHud != null && !controlsHud.isVisible()) {
            wakeKey = key; showControls();
            if (toggle != null && toggle.isEnabled()) toggle.requestFocus(); else back.requestFocus();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN || event.getAction() == KeyEvent.ACTION_UP) showControls();
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (video == null) return super.onKeyDown(key, event);
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || ((key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) && video.hasFocus())) { togglePlayback(); showControls(); return true; }
        if (key == KeyEvent.KEYCODE_MEDIA_NEXT) { move(1); return true; }
        if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { move(-1); return true; }
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY) { if (!castCommand("play", 0) && prepared) { userPaused = false; video.start(); updateButtons(); } return true; }
        if (key == KeyEvent.KEYCODE_MEDIA_PAUSE) { if (!castCommand("pause", 0) && prepared) { userPaused = true; video.pause(); saveProgress(); updateButtons(); } return true; }
        if (key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || key == KeyEvent.KEYCODE_MEDIA_REWIND) { seekBy(key == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? 30000 : -10000); return true; }
        if (video.hasFocus() && (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT)) { seekBy(key == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000); return true; }
        if (video.hasFocus() && (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN)) { showControls(); (toggle == null ? back : toggle).requestFocus(); return true; }
        return super.onKeyDown(key, event);
    }
    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (BuildConfig.IS_TV || android.os.Build.VERSION.SDK_INT < 26 || casting()
            || !prepared || video == null || !video.isPlaying() || isFinishing()
            || !getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return;
        try {
            closePipControls();
            pipControls = new PipPlaybackControls(this, this::setPipPaused, this::skipPipEpisode);
            updatePipControls();
            if (!enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder().build())) closePipControls();
        } catch (IllegalStateException | SecurityException denied) {
            closePipControls();
        }
    }

    private void updatePipControls() {
        if (pipControls == null || episode == null) return;
        String title = series.optString("title") + " · 第 " + episode.optInt("index") + " 集";
        int position = prepared ? Math.max(0, video.getCurrentPosition()) : resumeAt;
        pipControls.update(title, current, queue.size(), userPaused || completed, prepared && !buffering,
            position, Float.isNaN(actualSpeed) ? requestedSpeed : actualSpeed);
    }

    private void setPipPaused(boolean paused) {
        if (pipControls == null || !isInPictureInPictureMode() || destroyed) return;
        if (!paused && completed) { togglePlayback(); return; }
        userPaused = paused;
        if (prepared) {
            if (paused) { video.pause(); saveProgress(); }
            else video.start();
        }
        updateButtons();
    }

    private void skipPipEpisode(int delta) {
        int target = current + delta;
        if (pipControls != null && isInPictureInPictureMode() && target >= 0 && target < queue.size())
            loadAt(target, null, 0, userPaused);
    }

    private void closePipControls() {
        if (pipControls != null) pipControls.close();
        pipControls = null;
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration configuration) {
        super.onPictureInPictureModeChanged(inPip, configuration);
        if (!inPip) closePipControls();
        updateFullscreen();
        if (surfaceTaps != null) surfaceTaps.cancel();
        if (controlsHud != null) {
            controlsHud.hold(false);
            controlsHud.interact();
        }
        updateResumeOverlay();
        if (danmaku != null && inPip) danmaku.suspend();
    }

    private void handoffPlayback() { handoffPlayback(false); }

    private void handoffPlayback(boolean allowPaused) {
        boolean advancing = completed && seasonLookup != null;
        if (BuildConfig.IS_TV || handedOff || backgroundId == null || episode == null
            || (userPaused && !allowPaused && !advancing) || (completed && !advancing) || casting() || isFinishing() || destroyed) return;
        BackgroundPlaybackSession request = new BackgroundPlaybackSession(backgroundId, getIntent(),
            queueSeries, queue, current, resumeAt, actualQuality, automaticCeiling, requestedSpeed,
            autoplay, cleanup, offline, adoptedMedia == null ? null : adoptedMedia.uri);
        request.paused = userPaused;
        request.completed = request.advancing = advancing;
        handedOff = BackgroundPlaybackService.begin(this, request);
        if (handedOff) cancelSeasonLookup();
    }

    private boolean resumeFromService() {
        BackgroundPlaybackSession request = BackgroundPlaybackService.take(backgroundId);
        if (request == null) { handedOff = false; return false; }
        JSONObject previous = request.episode();
        int match = PlaybackPlaylist.find(queue, request.owner().optString("seriesId"),
            previous.optString("vid"), previous.optInt("index"), queueSeries);
        if (match < 0) {
            try {
                Intent resumed = new Intent(request.reopen)
                    .putExtra("episode", previous.toString()).putExtra("backgroundPosition", request.position)
                    .putExtra("backgroundPaused", request.paused || request.completed).putExtra("fullscreen", fullscreen);
                resumed.removeFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(resumed);
                finish();
                return true;
            } catch (RuntimeException unavailable) { handedOff = false; return false; }
        }
        handedOff = true;
        autoplay = request.autoplay;
        actualQuality = request.quality; automaticCeiling = request.ceiling; requestedSpeed = request.speed;
        if (request.advancing && request.completed) {
            current = match; episode = queue.get(match); series = owner(episode);
            completed = true; userPaused = request.paused;
            if (continueSeason(request.paused)) { handedOff = false; return true; }
        }
        loadAt(match, null, request.position, request.paused || request.completed);
        handedOff = false;
        return true;
    }

    @Override protected void onPause() {
        if (recommendationPrefetch != null) recommendationPrefetch.cancel();
        active = isInPictureInPictureMode();
        if (!active) cancelPlaybackWatchdogs();
        if (danmaku != null) danmaku.suspend();
        touchingControls = false;
        if (surfaceTaps != null) surfaceTaps.cancel();
        wakeKey = KeyEvent.KEYCODE_UNKNOWN;
        if (controlsHud != null) controlsHud.suspend();
        saveProgress();
        if (!active) handoffPlayback();
        if (!active && !casting() && video != null && prepared) video.pause();
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); active = true; resumeFromService(); if (isFinishing()) return; if (decoderPreparing && !casting() && !castWasActive) armPreparationTimeout(generation, video); updateBufferingWatchdog(generation, video); if (!casting() && prepared && !completed && !userPaused) video.start(); showControls(); }
    @Override protected void onStop() {
        active = false;
        closePipControls();
        saveProgress();
        handoffPlayback();
        if (handedOff && video != null && prepared) video.pause();
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) { savePlaybackState(state); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        cancelSeasonLookup();
        if (recommendationPrefetch != null) recommendationPrefetch.close();
        if (systemBackCleanup != null) systemBackCleanup.run();
        closePipControls();
        if (handedOff) android.util.Log.i("HongguoBackground", "播放器页面已销毁，服务继续持有播放会话");
        // Keep file deletion/update blocked until all media handles have been released.
        SoftDecoder.stop(softLayer); softLayer = null; softActive = false;
        if (danmaku != null) danmaku.close();
        detachCast();
        if (controlsHud != null) controlsHud.destroy();
        cancelQualitySwitch(); cancelPlaybackWatchdogs(); decoderPreparing = false; destroyed = true; ++generation; discardPendingMedia(); main.removeCallbacksAndMessages(null);
        if (video != null) video.stopPlayback(); releaseCurrent(); closeLocalWindow(); resolver.shutdownNow(); cacheWorker.shutdownNow();
        if (registeredPlayer) { registeredPlayer = false; LIVE_PLAYERS.decrementAndGet(); }
        super.onDestroy();
    }
}
