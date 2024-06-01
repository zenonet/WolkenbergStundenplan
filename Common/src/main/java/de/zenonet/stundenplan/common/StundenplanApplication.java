package de.zenonet.stundenplan.common;

import android.app.Application;

import java.time.Instant;

public class StundenplanApplication extends Application {
    public static Instant applicationEntrypointInstant;
    public static StundenplanApplication application;
    @Override
    public void onCreate() {
        super.onCreate();
        applicationEntrypointInstant = Instant.now();
        Utils.CachePath = this.getCacheDir().getPath();
        application = this;
    }
}
