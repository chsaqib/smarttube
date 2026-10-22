package com.liskovsoft.smartyoutubetv2.phone;

import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;

/** Keeps upstream service and player initialization, with a phone home screen. */
public class PhoneApplication extends MainApplication {
    @Override public void onCreate() {
        super.onCreate();
        RxHelper.setupGlobalErrorHandler();
        GlobalPreferences.instance(this);
        VideoStateService.instance(this);
        ViewManager manager = ViewManager.instance(this);
        manager.setRoot(PhoneActivity.class);
        manager.register(BrowseView.class, PhoneActivity.class);
        // Keep inherited PlaybackActivity parent resolution on the phone home.
        manager.register(PlaybackView.class, PlaybackActivity.class, PhoneActivity.class);
        manager.register(PlaybackView.class, PhoneWatchActivity.class, PhoneActivity.class);
    }
}
