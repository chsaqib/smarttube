package com.liskovsoft.smartyoutubetv2.phone;

import android.app.Activity;
import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.tv.R;

/** Standalone host for the same feed used beneath the in-app miniplayer. */
public class PhoneActivity extends Activity {
    private PhoneFeed feed;
    @Override protected void onCreate(Bundle state) { super.onCreate(state); showFeed(state); }
    private void showFeed(Bundle state) {
        if (feed != null) feed.destroy();
        setContentView(R.layout.phone_home);
        feed = new PhoneFeed(this, findViewById(android.R.id.content), state);
    }
    @Override protected void onResume() {
        super.onResume();
        ViewManager.instance(this).addTop(this);
        Bundle returned = PhoneFeed.takeReturnedState(this);
        if (returned != null) showFeed(returned);
    }
    @Override protected void onSaveInstanceState(Bundle state) { feed.saveState(state); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        feed.destroy();
        if (isFinishing()) ViewManager.instance(this).removeTop(this);
        super.onDestroy();
    }
    @Override public void onBackPressed() { if (!feed.handleBack()) super.onBackPressed(); }
}
