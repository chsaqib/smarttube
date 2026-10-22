package com.liskovsoft.smartyoutubetv2.phone;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.Rational;
import android.view.ViewConfiguration;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Phone watch page: pinned 16:9 video, scrollable details, native touch controls. */
public class PhoneWatchActivity extends PlaybackActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Video> related = new ArrayList<>();
    private final WatchAdapter adapter = new WatchAdapter();
    private PhonePlaybackFragment player;
    private View videoPanel, controls, details, loading;
    private RecyclerView recommendations;
    private ImageButton playButton, expandButton;
    private SeekBar seek;
    private TextView time;
    private boolean dragging, expandedDescription, resumed;
    private String headerSignature, channelImage;
    private String currentVideoId;
    private boolean minimized;
    private PhoneFeed browsingFeed;
    private View browsingView, miniBar;
    private ImageButton miniPlay;
    private TextView miniTitle;
    private ProgressBar miniProgress;
    private View playerContainer;
    private float miniX = 1, miniY = 1, downX, downY, startX, startY;
    private boolean movingMini, pipSession;
    private final Runnable hideControls = () -> {
        if (!dragging && player != null && player.isPlaying()) showTouchControls(false);
    };
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            updateProgress();
            refreshVideo();
            handler.postDelayed(this, 500);
        }
    };

    @Override protected boolean useTvDensity() { return false; }
    @Override protected void initTheme() { setTheme(R.style.PhoneWatchTheme); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        player = (PhonePlaybackFragment) getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        videoPanel = findViewById(R.id.watch_video_panel);
        playerContainer = findViewById(R.id.player_container);
        controls = findViewById(R.id.watch_controls);
        details = findViewById(R.id.watch_details);
        loading = findViewById(R.id.watch_loading);
        recommendations = findViewById(R.id.watch_details);
        recommendations.setLayoutManager(new LinearLayoutManager(this));
        recommendations.setAdapter(adapter);
        playButton = findViewById(R.id.watch_play);
        expandButton = findViewById(R.id.watch_expand);
        seek = findViewById(R.id.watch_seek);
        time = findViewById(R.id.watch_time);
        miniBar = findViewById(R.id.mini_bar);
        miniPlay = findViewById(R.id.mini_play);
        miniTitle = findViewById(R.id.mini_title);
        miniProgress = findViewById(R.id.mini_progress);
        minimized = state != null && state.getBoolean("minimized");
        if (state != null) { miniX = state.getFloat("miniX", 1); miniY = state.getFloat("miniY", 1); }
        miniBar.setOnClickListener(v -> expandWatch());
        miniBar.setOnTouchListener(this::dragMiniPlayer);
        miniPlay.setOnClickListener(v -> playButton.performClick());
        findViewById(R.id.mini_close).setOnClickListener(v -> closeMiniPlayer());
        if (minimized) ensureBrowsingFeed(state.getBundle("browsingFeed"));
        expandedDescription = state != null && state.getBoolean("descriptionExpanded");
        View touchTarget = findViewById(R.id.watch_touch_target);
        touchTarget.setOnClickListener(v -> {
            if (minimized) expandWatch(); else showTouchControls(!areTouchControlsShown());
        });
        GestureDetector gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent event) { touchTarget.performClick(); return true; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                if (minimized) { expandWatch(); return true; }
                skip(event.getX() < touchTarget.getWidth() / 2f ? -10000 : 10000);
                return true;
            }
        });
        touchTarget.setOnTouchListener((view, event) -> minimized && !pip()
                ? dragMiniPlayer(view, event) : gestures.onTouchEvent(event));
        findViewById(R.id.watch_back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.watch_settings).setOnClickListener(v -> showSettings());
        findViewById(R.id.watch_rewind).setOnClickListener(v -> skip(-10000));
        findViewById(R.id.watch_forward).setOnClickListener(v -> skip(10000));
        expandButton.setOnClickListener(v -> toggleFullscreen());
        playButton.setOnClickListener(v -> {
            if (player == null || !player.isEngineInitialized()) return;
            boolean play = !player.getPlayWhenReady();
            player.setPlayWhenReady(play);
            PlaybackPresenter presenter = PlaybackPresenter.instance(this);
            if (play) presenter.onPlayClicked(); else presenter.onPauseClicked();
            updateProgress();
            showTouchControls(true);
        });
        seek.setMax(1000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; handler.removeCallbacks(hideControls); }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null && player.getDurationMs() > 0) {
                    time.setText(clock(player.getDurationMs() * progress / 1000) + " / " + clock(player.getDurationMs()));
                }
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.getDurationMs() > 0) {
                    player.setPositionMs(player.getDurationMs() * bar.getProgress() / 1000);
                    PlaybackPresenter.instance(PhoneWatchActivity.this).onSeekEnd();
                }
                dragging = false;
                showTouchControls(true);
            }
        });
        findViewById(R.id.watch_root).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) handler.post(this::applyWatchLayout);
        });
        videoPanel.post(this::applyWatchLayout);
        refreshVideo();
        showTouchControls(true);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!pip()) pipSession = false;
        applyWatchLayout();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(hideControls);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (browsingFeed != null) browsingFeed.destroy();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public void onUserLeaveHint() { enterPhonePip(); }
    private boolean enterPhonePip() {
        if (pip()) return true;
        if (android.os.Build.VERSION.SDK_INT < 26 || isFinishing() || player == null
                || !player.isEngineInitialized() || !player.getPlayWhenReady()
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false;
        Rect source = new Rect();
        videoPanel.getGlobalVisibleRect(source);
        try {
            pipSession = enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).setSourceRectHint(source).build());
            return pipSession;
        } catch (IllegalStateException | IllegalArgumentException error) {
            pipSession = false;
            return false;
        }
    }
    @Override protected void onStop() {
        // Dismissing Android's PiP window must stop audio even if upstream background playback is enabled.
        if (pipSession && !isChangingConfigurations()) {
            pipSession = false;
            if (player != null && player.isEngineInitialized()) player.setPlayWhenReady(false);
            if (player != null) player.blockEngine(false);
            finishReally();
        }
        super.onStop();
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        applyWatchLayout();
    }
    @Override public void onPictureInPictureModeChanged(boolean pip) {
        super.onPictureInPictureModeChanged(pip);
        applyWatchLayout();
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) {
        state.putBoolean("descriptionExpanded", expandedDescription);
        state.putBoolean("minimized", minimized);
        state.putFloat("miniX", miniX);
        state.putFloat("miniY", miniY);
        if (browsingFeed != null) {
            Bundle feedState = new Bundle();
            browsingFeed.saveState(feedState);
            state.putBundle("browsingFeed", feedState);
        }
        super.onSaveInstanceState(state);
    }
    private boolean fullscreen() { return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE; }
    private boolean pip() { return android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode(); }
    private void toggleFullscreen() {
        setRequestedOrientation(fullscreen() ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
    @Override public void onBackPressed() {
        if (fullscreen() && !pip()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else if (!minimized) minimizePlayer();
        else if (browsingFeed == null || !browsingFeed.handleBack()) {
            if (enterPhonePip()) startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
            else closeMiniPlayer();
        }
    }

    private void ensureBrowsingFeed(Bundle state) {
        if (browsingFeed != null) return;
        browsingView = ((ViewStub) findViewById(R.id.mini_feed_stub)).inflate();
        browsingView.setFitsSystemWindows(false);
        browsingFeed = new PhoneFeed(this, browsingView, state != null ? state : PhoneFeed.lastState(this));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void minimizePlayer() {
        ensureBrowsingFeed(null);
        minimized = true;
        showTouchControls(false);
        applyWatchLayout();
    }
    private void expandWatch() {
        minimized = false;
        applyWatchLayout();
        showTouchControls(true);
    }
    public void openFromFeed(Video video) {
        expandWatch();
        PlaybackPresenter.instance(this).openVideo(video);
    }
    private void closeMiniPlayer() {
        if (browsingFeed != null) browsingFeed.rememberState(true);
        if (player != null && player.isEngineInitialized()) player.setPlayWhenReady(false);
        finishReally();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // The inherited TV fragment otherwise consumes Back to dismiss its rows first.
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) onBackPressed();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void finish() { if (pip() || pipSession) closeMiniPlayer(); else onBackPressed(); }

    private boolean dragMiniPlayer(View view, MotionEvent event) {
        if (!minimized || pip()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX(); downY = event.getRawY();
                startX = playerContainer.getX(); startY = playerContainer.getY();
                movingMini = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (Math.hypot(dx, dy) > ViewConfiguration.get(this).getScaledTouchSlop()) movingMini = true;
                if (movingMini) {
                    View root = findViewById(R.id.watch_root);
                    float maxX = Math.max(1, root.getWidth() - playerContainer.getWidth());
                    float maxY = Math.max(1, root.getHeight() - dp(56) - playerContainer.getHeight());
                    miniX = Math.max(0, Math.min(1, (startX + dx) / maxX));
                    miniY = Math.max(0, Math.min(1, (startY + dy) / maxY));
                    positionMiniPlayer();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!movingMini) view.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL: return true;
            default: return false;
        }
    }
    private void positionMiniPlayer() {
        View root = findViewById(R.id.watch_root);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playerContainer.getLayoutParams();
        params.leftMargin = Math.round(Math.max(0, root.getWidth() - params.width) * miniX);
        params.topMargin = Math.round(Math.max(0, root.getHeight() - dp(56) - params.height) * miniY);
        playerContainer.setLayoutParams(params);
    }

    private void applyWatchLayout() {
        if (videoPanel == null) return;
        boolean floating = minimized && !pip();
        boolean expanded = pip() || (!minimized && fullscreen());
        View root = findViewById(R.id.watch_root);
        // Decor handles portrait system bars; the video fills every pixel in fullscreen.
        root.setFitsSystemWindows(false);
        root.setPadding(0, 0, 0, 0);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), !expanded);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) videoPanel.getLayoutParams();
        int width = root.getWidth();
        int miniWidth = Math.min(dp(220), Math.max(dp(160), width - dp(16)));
        int height = floating ? Math.round(miniWidth * 9f / 16f) : expanded ? ViewGroup.LayoutParams.MATCH_PARENT : Math.round(width * 9f / 16f);
        FrameLayout.LayoutParams container = (FrameLayout.LayoutParams) playerContainer.getLayoutParams();
        container.width = floating ? miniWidth : ViewGroup.LayoutParams.MATCH_PARENT;
        container.height = floating ? height + dp(40) : ViewGroup.LayoutParams.MATCH_PARENT;
        container.gravity = Gravity.TOP | Gravity.LEFT;
        container.leftMargin = container.topMargin = 0;
        playerContainer.setLayoutParams(container);
        if (floating) positionMiniPlayer();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = width > 0 ? height : dp(220);
        params.gravity = Gravity.TOP;
        params.bottomMargin = 0;
        videoPanel.setLayoutParams(params);
        FrameLayout.LayoutParams content = (FrameLayout.LayoutParams) details.getLayoutParams();
        content.topMargin = Math.max(0, height);
        details.setLayoutParams(content);
        details.setVisibility(expanded || minimized ? View.GONE : View.VISIBLE);
        if (browsingView != null) browsingView.setVisibility(floating ? View.VISIBLE : View.GONE);
        miniBar.setVisibility(floating ? View.VISIBLE : View.GONE);
        miniProgress.setVisibility(floating ? View.VISIBLE : View.GONE);
        findViewById(R.id.watch_touch_target).setContentDescription(getString(minimized ? R.string.mini_expand : R.string.watch_toggle_controls));
        if (expanded) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        if (expanded) {
            bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            bars.hide(WindowInsetsCompat.Type.systemBars());
        } else bars.show(WindowInsetsCompat.Type.systemBars());
        expandButton.setImageResource(expanded ? R.drawable.watch_fullscreen_exit : R.drawable.watch_fullscreen);
        expandButton.setContentDescription(getString(expanded ? R.string.watch_exit_fullscreen : R.string.watch_fullscreen));
        if (pip()) showTouchControls(false);
    }

    public void showTouchControls(boolean show) {
        if (controls == null) return;
        controls.setVisibility(show && !minimized && !pip() ? View.VISIBLE : View.GONE);
        handler.removeCallbacks(hideControls);
        if (show) handler.postDelayed(hideControls, 3500);
    }
    public boolean areTouchControlsShown() { return controls != null && controls.getVisibility() == View.VISIBLE; }
    public void showLoading(boolean show) { if (loading != null) loading.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void skip(long offset) {
        if (player == null || !player.isEngineInitialized()) return;
        long duration = player.getDurationMs();
        long position = Math.max(0, player.getPositionMs() + offset);
        if (duration > 0) position = Math.min(duration, position);
        player.setPositionMs(position);
        PlaybackPresenter.instance(this).onSeekEnd();
        showTouchControls(true);
        updateProgress();
    }
    private void updateProgress() {
        if (player == null || playButton == null) return;
        boolean ready = player.isEngineInitialized();
        boolean playing = ready && player.getPlayWhenReady();
        playButton.setImageResource(playing ? R.drawable.watch_pause : R.drawable.watch_play);
        playButton.setContentDescription(getString(playing ? R.string.watch_pause : R.string.watch_play));
        miniPlay.setImageResource(playing ? R.drawable.watch_pause : R.drawable.watch_play);
        miniPlay.setContentDescription(getString(playing ? R.string.watch_pause : R.string.watch_play));
        long duration = ready ? player.getDurationMs() : 0;
        long position = ready ? Math.max(0, player.getPositionMs()) : 0;
        miniProgress.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);

        seek.setEnabled(duration > 0);
        if (!dragging) {
            seek.setProgress(duration > 0 ? (int) Math.min(1000, position * 1000 / duration) : 0);
            Video video = player.getVideo();
            time.setText(video != null && video.isLive ? getString(R.string.watch_live) : clock(position) + " / " + clock(duration));
        }
    }
    static String clock(long milliseconds) {
        if (milliseconds <= 0) return "0:00";
        long seconds = milliseconds / 1000;
        return seconds >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    public void refreshVideo() {
        if (recommendations == null) return;
        Video video = player != null ? player.getVideo() : null;
        if (video == null) video = PlaybackPresenter.instance(this).getVideo();
        if (video == null) return;
        miniTitle.setText(video.getTitleFull());
        if (!Objects.equals(currentVideoId, video.videoId)) {
            currentVideoId = video.videoId;
            expandedDescription = false;
            channelImage = null;
            recommendations.scrollToPosition(0);
        }
        String signature = video.getTitleFull() + "|" + video.getSecondTitleFull() + "|" + video.getAuthor() + "|" + video.description + "|" + channelImage;
        if (!signature.equals(headerSignature)) {
            headerSignature = signature;
            adapter.notifyItemChanged(0);
        }
    }
    public void setChannelImage(String url) { channelImage = url; refreshVideo(); }
    public void refreshRelated() {
        if (player == null || recommendations == null) return;
        List<Video> videos = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; ; index++) {
            VideoGroup group = player.getSuggestionsByIndex(index);
            if (group == null) break;
            if (group.getVideos() == null) continue;
            for (Video video : group.getVideos()) {
                if (video.videoId != null && !video.videoId.equals(currentVideoId) && seen.add(video.videoId)) videos.add(video);
            }
        }
        related.clear();
        related.addAll(videos);
        adapter.notifyDataSetChanged();
    }
    public void showRelated() {
        if (minimized) return;
        if (fullscreen()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (recommendations != null) recommendations.smoothScrollToPosition(1);
    }
    private void share() {
        Video video = player != null ? player.getVideo() : null;
        if (video == null || video.videoId == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=" + video.videoId);
        startActivity(Intent.createChooser(intent, getString(R.string.watch_share)));
    }
    private void showSettings() {
        handler.removeCallbacks(hideControls);
        new AlertDialog.Builder(this).setTitle(R.string.watch_settings)
                .setItems(new String[]{getString(R.string.watch_quality), getString(R.string.watch_speed), getString(R.string.watch_captions)}, (d, index) -> {
                    if (index == 0) showFormats(false); else if (index == 1) showSpeed(); else showFormats(true);
                }).show();
    }
    private void showFormats(boolean subtitles) {
        if (player == null) return;
        List<FormatItem> available = subtitles ? player.getSubtitleFormats() : player.getVideoFormats();
        List<FormatItem> formats = new ArrayList<>();
        if (available != null) for (FormatItem format : available) {
            // The upstream default video entry disables the video track; omit it here.
            if (format != null && (subtitles || !format.isDefault())) formats.add(format);
        }
        if (formats.isEmpty()) { Toast.makeText(this, R.string.watch_wait, Toast.LENGTH_SHORT).show(); return; }
        CharSequence[] labels = new CharSequence[formats.size()];
        FormatItem current = subtitles ? player.getSubtitleFormat() : player.getVideoFormat();
        int selected = -1;
        for (int i = 0; i < labels.length; i++) {
            FormatItem format = formats.get(i);
            CharSequence label = format.getTitle();
            if (TextUtils.isEmpty(label)) {
                label = subtitles ? (TextUtils.isEmpty(format.getLanguage()) ? getString(R.string.watch_captions_off) : format.getLanguage())
                        : (format.getHeight() > 0 ? format.getHeight() + "p" : getString(R.string.watch_quality));
            }
            labels[i] = label;
            if (format.isSelected() || format.equals(current)) selected = i;
        }
        new AlertDialog.Builder(this).setTitle(subtitles ? R.string.watch_captions : R.string.watch_quality)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    FormatItem format = formats.get(which);
                    player.setFormat(format);
                    PlayerData.instance(this).setFormat(format);
                    dialog.dismiss();
                    showTouchControls(true);
                }).setNegativeButton(android.R.string.cancel, null).show();
    }
    private void showSpeed() {
        if (player == null) return;
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
        String[] labels = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};
        int selected = 2;
        for (int i = 0; i < speeds.length; i++) if (Math.abs(player.getSpeed() - speeds[i]) < .01f) selected = i;
        new AlertDialog.Builder(this).setTitle(R.string.watch_speed).setSingleChoiceItems(labels, selected, (dialog, which) -> {
            player.setSpeed(speeds[which]); dialog.dismiss(); showTouchControls(true);
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private class WatchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() { return related.size() + 1; }
        @Override public int getItemViewType(int position) { return position == 0 ? 0 : 1; }
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View view = LayoutInflater.from(parent.getContext()).inflate(type == 0 ? R.layout.phone_watch_details : R.layout.phone_video, parent, false);
            return new RecyclerView.ViewHolder(view) {};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View view = holder.itemView;
            if (position == 0) {
                Video video = player != null ? player.getVideo() : null;
                if (video == null) video = PlaybackPresenter.instance(PhoneWatchActivity.this).getVideo();
                if (video == null) return;
                ((TextView) view.findViewById(R.id.watch_title)).setText(video.getTitleFull());
                ((TextView) view.findViewById(R.id.watch_metadata)).setText(video.getSecondTitleFull());
                ((TextView) view.findViewById(R.id.watch_channel)).setText(video.getAuthor());
                ImageView avatar = view.findViewById(R.id.watch_avatar);
                Glide.with(PhoneWatchActivity.this).load(channelImage).circleCrop().into(avatar);
                TextView description = view.findViewById(R.id.watch_description);
                description.setText(TextUtils.isEmpty(video.description) ? video.getSecondTitleFull() : video.description);
                description.setMaxLines(expandedDescription ? Integer.MAX_VALUE : 2);
                View.OnClickListener expand = v -> { expandedDescription = !expandedDescription; notifyItemChanged(0); };
                view.findViewById(R.id.watch_title).setOnClickListener(expand);
                description.setOnClickListener(expand);
                view.findViewById(R.id.watch_share).setOnClickListener(v -> share());
                view.findViewById(R.id.watch_quality).setOnClickListener(v -> showFormats(false));
                view.findViewById(R.id.watch_speed).setOnClickListener(v -> showSpeed());
                view.findViewById(R.id.watch_related_label).setVisibility(related.isEmpty() ? View.GONE : View.VISIBLE);
            } else {
                Video video = related.get(position - 1);
                ((TextView) view.findViewById(R.id.phone_video_title)).setText(video.getTitleFull());
                ((TextView) view.findViewById(R.id.phone_video_details)).setText(video.getSecondTitleFull());
                TextView badge = view.findViewById(R.id.phone_video_badge);
                badge.setText(video.badge);
                badge.setVisibility(TextUtils.isEmpty(video.badge) ? View.GONE : View.VISIBLE);
                Glide.with(PhoneWatchActivity.this).load(video.cardImageUrl).centerCrop().into((ImageView) view.findViewById(R.id.phone_thumbnail));
                view.setOnClickListener(v -> {
                    // Open in the existing engine; do not create another watch activity.
                    PlaybackPresenter.instance(PhoneWatchActivity.this).openVideo(video);
                    refreshVideo();
                    showTouchControls(true);
                });
            }
        }
        @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            ImageView image = holder.itemView.findViewById(R.id.phone_thumbnail);
            if (image == null) image = holder.itemView.findViewById(R.id.watch_avatar);
            if (image != null) Glide.with(PhoneWatchActivity.this).clear(image);
        }
    }
}
