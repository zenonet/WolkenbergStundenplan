package de.zenonet.stundenplan.broadcastReceivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import de.zenonet.stundenplan.StundenplanPhoneApplication;

public class BootCompletedReceiver extends BroadcastReceiver {

    // Nobody cares if updates are rescheduled by a   app lol
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        ((StundenplanPhoneApplication) StundenplanPhoneApplication.application).scheduleUpdateRepeating();
    }
}