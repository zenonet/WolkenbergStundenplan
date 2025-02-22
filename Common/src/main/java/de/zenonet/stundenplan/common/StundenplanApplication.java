package de.zenonet.stundenplan.common;

import android.app.Application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager;
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException;

public class StundenplanApplication extends Application {
    public static Instant applicationEntrypointInstant;
    public static int millisToDisplay = -1;
    public static StundenplanApplication application;
    private static TimeTableManager AuxiliaryManager;
    public static TimeTableManager getAuxiliaryManager() {
        if(AuxiliaryManager != null) return AuxiliaryManager;
        AuxiliaryManager = new TimeTableManager();
        try {
            AuxiliaryManager.init(application);
            return AuxiliaryManager;
        } catch (UserLoadException e) {
            throw new RuntimeException(e);
        }
    }

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
