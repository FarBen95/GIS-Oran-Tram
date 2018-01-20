package com.farouk.sig;

import android.content.Intent;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by USER on 08/01/2018.
 */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onStart() {
        super.onStart();

        TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(new Intent(this, MainActivity.class))
                .addNextIntent(new Intent(this, IntroActivity.class))
                .startActivities();
    }
}
