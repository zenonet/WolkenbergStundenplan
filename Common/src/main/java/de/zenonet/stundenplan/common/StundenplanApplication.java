package de.zenonet.stundenplan.common;

import android.app.Application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class StundenplanApplication extends Application {
    public static Instant applicationEntrypointInstant;
    public static int millisToDisplay = -1;
    public static StundenplanApplication application;
    @Override
    public void onCreate() {
        super.onCreate();
        applicationEntrypointInstant = Instant.now();
        Utils.CachePath = this.getCacheDir().getPath();
        application = this;
    }

    public static int getMillisSinceAppStart(){
        return (int) ChronoUnit.MILLIS.between(applicationEntrypointInstant, Instant.now());
    }
}
