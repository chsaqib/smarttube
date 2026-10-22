package com.liskovsoft.smartyoutubetv2.phone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment;

/** Reuses SmartTube's engine/controllers while the activity owns the touch UI. */
public class PhonePlaybackFragment extends PlaybackFragment {
    private PhoneWatchActivity watch() {
        return getActivity() instanceof PhoneWatchActivity ? (PhoneWatchActivity) getActivity() : null;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        View root = super.onCreateView(inflater, parent, state);
        hideTvChrome(root);
        SubtitleView subtitles = root.findViewById(R.id.leanback_subtitles);
        if (subtitles != null) {
            int inset = (int) (12 * getResources().getDisplayMetrics().density);
            subtitles.setPadding(inset, 0, inset, inset);
            subtitles.setFractionalTextSize(0.0533f);
        }
        return root;
    }

    private void hideTvChrome(View root) {
        if (root == null) return;
        View dock = root.findViewById(R.id.playback_controls_dock);
        if (dock != null) dock.setVisibility(View.GONE);
        View background = root.findViewById(R.id.playback_fragment_background);
        if (background != null) background.setVisibility(View.GONE);
    }

    @Override public void onDispatchTouchEvent(MotionEvent event) { /* Activity handles touch. */ }
    @Override public void showControlsOverlay(boolean animate) {
        super.hideControlsOverlay(false);
        hideTvChrome(getView());
        if (watch() != null) watch().showTouchControls(true);
    }
    @Override public void hideControlsOverlay(boolean animate) {
        super.hideControlsOverlay(false);
        if (watch() != null) watch().showTouchControls(false);
    }
    @Override public void showOverlay(boolean show) {
        if (show) showControlsOverlay(false); else hideControlsOverlay(false);
    }
    @Override public void showControls(boolean show) { showOverlay(show); }
    @Override public void showSuggestions(boolean show) {
        if (show && watch() != null) watch().showRelated();
    }
    @Override public boolean isOverlayShown() { return watch() != null && watch().areTouchControlsShown(); }
    @Override public boolean isControlsShown() { return isOverlayShown(); }
    @Override public boolean isSuggestionsShown() { return false; }
    @Override public void showProgressBar(boolean show) {
        if (watch() != null) watch().showLoading(show);
    }
    @Override public void setVideo(Video video) {
        super.setVideo(video);
        if (watch() != null) watch().refreshVideo();
    }
    @Override public void setTitle(String title) {
        super.setTitle(title);
        if (watch() != null) watch().refreshVideo();
    }
    @Override public void setChannelIcon(String url) {
        super.setChannelIcon(url);
        if (watch() != null) watch().setChannelImage(url);
    }
    @Override public void updateSuggestions(VideoGroup group) {
        super.updateSuggestions(group);
        if (watch() != null) watch().refreshRelated();
    }
    @Override public void removeSuggestions(VideoGroup group) {
        super.removeSuggestions(group);
        if (watch() != null) watch().refreshRelated();
    }
    @Override public void clearSuggestions() {
        super.clearSuggestions();
        if (watch() != null) watch().refreshRelated();
    }
}
