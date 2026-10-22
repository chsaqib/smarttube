package com.liskovsoft.smartyoutubetv2.phone;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** Native touch feed backed by YouTube content service. */
public class PhoneFeed {
    private final Activity activity;
    private final View root;
    private int restorePosition;
    private final List<MediaItem> items = new ArrayList<>();
    private final List<MediaGroup> groups = new ArrayList<>();
    private final Set<String> videoIds = new HashSet<>();
    private final FeedAdapter adapter = new FeedAdapter();
    private Disposable request;
    private EditText search;
    private TextView status;
    private Button more;
    private View progress;
    private RecyclerView feed;
    private ContentService service;
    private boolean loading;
    private boolean failed;
    private boolean paginating;
    private int pageGroup;
    private String query = "";
    private boolean trendingMode = false;

    public PhoneFeed(Activity activity, View root, Bundle savedState) {
        this.activity = activity;
        this.root = root;
        GlobalPreferences.instance(activity);
        service = YouTubeServiceManager.instance().getContentService();
        search = findViewById(R.id.phone_search);
        status = findViewById(R.id.phone_status);
        progress = findViewById(R.id.phone_progress);
        more = findViewById(R.id.phone_more);
        feed = findViewById(R.id.phone_feed);
        feed.setLayoutManager(new LinearLayoutManager(activity));
        feed.setAdapter(adapter);

        findViewById(R.id.phone_search_button).setOnClickListener(v -> submitSearch());
        search.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) { submitSearch(); return true; }
            return false;
        });

        View searchIcon = findViewById(R.id.phone_header_search_icon);
        if (searchIcon != null) {
            searchIcon.setOnClickListener(v -> focusSearch());
        }

        View homeBtn = findViewById(R.id.phone_home_button);
        if (homeBtn != null) {
            homeBtn.setOnClickListener(v -> {
                trendingMode = false;
                search.setText("");
                query = "";
                load();
            });
        }

        View trendingBtn = findViewById(R.id.phone_trending_button);
        if (trendingBtn != null) {
            trendingBtn.setOnClickListener(v -> {
                trendingMode = true;
                search.setText("");
                query = "";
                load();
            });
        }

        View searchTabBtn = findViewById(R.id.phone_search_tab_button);
        if (searchTabBtn != null) {
            searchTabBtn.setOnClickListener(v -> focusSearch());
        }

        more.setOnClickListener(v -> {
            if (failed && !paginating) load(); else loadMore();
        });

        if (savedState != null) {
            query = savedState.getString("query", "");
            trendingMode = savedState.getBoolean("trendingMode", false);
            restorePosition = savedState.getInt("feedPosition", 0);
            search.setText(query);
        }
        load();
    }

    private void focusSearch() {
        search.requestFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }

    private <T extends View> T findViewById(int id) { return root.findViewById(id); }
    private Object getSystemService(String name) { return activity.getSystemService(name); }

    public void saveState(Bundle state) {
        state.putString("query", query);
        state.putBoolean("trendingMode", trendingMode);
        state.putInt("feedPosition", ((LinearLayoutManager) feed.getLayoutManager()).findFirstVisibleItemPosition());
    }
    public void destroy() { cancelRequest(); }
    public boolean handleBack() {
        if (query.isEmpty() && !trendingMode) return false;
        trendingMode = false;
        search.setText("");
        query = "";
        load();
        return true;
    }
    public void rememberState(boolean returnToFeed) {
        Bundle state = new Bundle();
        saveState(state);
        activity.getSharedPreferences("phone_browsing", Context.MODE_PRIVATE).edit()
                .putString("query", query).putBoolean("trendingMode", trendingMode)
                .putInt("feedPosition", state.getInt("feedPosition"))
                .putBoolean("returnToFeed", returnToFeed).apply();
    }
    public static Bundle lastState(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("phone_browsing", Context.MODE_PRIVATE);
        Bundle state = new Bundle();
        state.putString("query", prefs.getString("query", ""));
        state.putBoolean("trendingMode", prefs.getBoolean("trendingMode", false));
        state.putInt("feedPosition", prefs.getInt("feedPosition", 0));
        return state;
    }
    public static Bundle takeReturnedState(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("phone_browsing", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("returnToFeed", false)) return null;
        prefs.edit().putBoolean("returnToFeed", false).apply();
        return lastState(context);
    }
    private void submitSearch() {
        trendingMode = false;
        query = search.getText().toString().trim();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
        load();
    }

    private void cancelRequest() {
        if (request != null) request.dispose();
    }

    private void load() {
        cancelRequest();
        items.clear();
        groups.clear();
        videoIds.clear();
        pageGroup = 0;
        paginating = false;
        adapter.notifyDataSetChanged();
        feed.scrollToPosition(0);
        setLoading(true);

        if (trendingMode) {
            status.setText(R.string.phone_trending);
        } else if (query.isEmpty()) {
            status.setText(R.string.phone_loading_home);
        } else {
            status.setText(R.string.phone_searching);
        }

        final int positionToRestore = restorePosition;
        restorePosition = 0;
        final String requestedQuery = query;
        final boolean isTrending = trendingMode;

        request = Observable.defer(() -> {
                    if (isTrending) {
                        return service.getTrendingObserve();
                    } else if (requestedQuery.isEmpty()) {
                        return service.getHomeObserve().filter(PhoneFeed::hasVideos)
                                .switchIfEmpty(Observable.defer(service::getTrendingObserve));
                    } else {
                        return service.getSearchObserve(requestedQuery);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result != null) for (MediaGroup group : result) {
                        if (group == null) continue;
                        groups.add(group);
                        append(group);
                    }
                }, error -> showFailure(), () -> {
                    setLoading(false);
                    if (items.isEmpty()) {
                        status.setText(R.string.phone_empty);
                    } else if (isTrending) {
                        status.setText(R.string.phone_trending);
                    } else if (query.isEmpty()) {
                        status.setText(R.string.phone_home);
                    } else {
                        status.setText(R.string.phone_results);
                    }
                    updateMore();
                    if (positionToRestore > 0) feed.scrollToPosition(Math.min(positionToRestore, Math.max(0, items.size() - 1)));
                });
    }

    private void append(MediaGroup group) {
        List<MediaItem> incoming = group.getMediaItems();
        if (incoming == null) return;
        int start = items.size();
        for (MediaItem item : incoming) {
            if (item != null && item.getVideoId() != null && videoIds.add(item.getVideoId())) items.add(item);
        }
        adapter.notifyItemRangeInserted(start, items.size() - start);
    }

    private static boolean hasVideos(List<MediaGroup> result) {
        if (result != null) for (MediaGroup group : result) {
            if (group == null || group.getMediaItems() == null) continue;
            for (MediaItem item : group.getMediaItems()) {
                if (item != null && item.getVideoId() != null) return true;
            }
        }
        return false;
    }

    private void loadMore() {
        if (loading || pageGroup >= groups.size()) return;
        paginating = true;
        setLoading(true);
        final boolean[] received = {false};
        request = Observable.defer(() -> service.continueGroupObserve(groups.get(pageGroup)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(next -> {
                    received[0] = true;
                    if (next == null || next.isEmpty()) {
                        pageGroup++;
                    } else {
                        String previousKey = groups.get(pageGroup).getNextPageKey();
                        groups.set(pageGroup, next);
                        append(next);
                        String nextKey = next.getNextPageKey();
                        if (nextKey == null || nextKey.equals(previousKey)) pageGroup++;
                    }
                }, error -> showFailure(), () -> {
                    if (!received[0]) pageGroup++;
                    setLoading(false);
                    updateMore();
                });
    }

    private void setLoading(boolean value) {
        loading = value;
        if (value) failed = false;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        more.setEnabled(!value);
        if (value) more.setVisibility(View.GONE);
    }

    private void showFailure() {
        setLoading(false);
        failed = true;
        status.setText(R.string.phone_network_error);
        more.setText(R.string.phone_retry);
        more.setVisibility(View.VISIBLE);
    }

    private void updateMore() {
        while (pageGroup < groups.size() && groups.get(pageGroup).getNextPageKey() == null) pageGroup++;
        more.setText(R.string.phone_load_more);
        more.setVisibility(pageGroup < groups.size() ? View.VISIBLE : View.GONE);
    }

    private class FeedAdapter extends RecyclerView.Adapter<VideoHolder> {
        @NonNull @Override public VideoHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new VideoHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.phone_video, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull VideoHolder holder, int position) {
            MediaItem item = items.get(position);
            holder.title.setText(item.getTitle());
            holder.details.setText(item.getSecondTitle());

            String badgeText = item.getBadgeText();
            if (TextUtils.isEmpty(badgeText)) {
                holder.badge.setVisibility(View.GONE);
            } else {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText(badgeText);
            }

            Glide.with(activity)
                    .load(item.getCardImageUrl())
                    .centerCrop()
                    .into(holder.image);

            holder.itemView.setContentDescription(item.getTitle());
            holder.itemView.setOnClickListener(v -> {
                rememberState(false);
                Video video = Video.from(item);
                if (activity instanceof PhoneWatchActivity) ((PhoneWatchActivity) activity).openFromFeed(video);
                else PlaybackPresenter.instance(activity).openVideo(video);
            });
        }
        @Override public void onViewRecycled(@NonNull VideoHolder holder) {
            Glide.with(activity).clear(holder.image);
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private static class VideoHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView details;
        final TextView badge;
        final ImageView channelAvatar;

        VideoHolder(View view) {
            super(view);
            image = view.findViewById(R.id.phone_thumbnail);
            title = view.findViewById(R.id.phone_video_title);
            details = view.findViewById(R.id.phone_video_details);
            badge = view.findViewById(R.id.phone_video_badge);
            channelAvatar = view.findViewById(R.id.phone_channel_avatar);
        }
    }
}
