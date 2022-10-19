package com.breadwallet.legacy.presenter.activities.settings;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

import com.breadwallet.R;
import com.breadwallet.legacy.presenter.activities.util.BRActivity;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class NotificationActivity extends BRActivity {
    private static final String TAG = NotificationActivity.class.getName();
    private SwitchMaterial toggleButton;
    public static boolean appVisible = false;
    private static NotificationActivity app;

    public static NotificationActivity getApp() {
        return app;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        toggleButton = findViewById(R.id.toggleButton);
        toggleButton.setChecked(BRSharedPrefs.getShowNotification());
        toggleButton.setOnCheckedChangeListener((buttonView, isChecked) ->
                BRSharedPrefs.putShowNotification(isChecked)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        appVisible = true;
        app = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        appVisible = false;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
    }

}
