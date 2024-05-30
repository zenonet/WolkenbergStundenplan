package de.zenonet.stundenplan.common

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class Formatter(context: Context) {

    private var preference: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

    public fun formatTeacherName(name: String): String {
        if (!preference
                .getBoolean("showTeacherFirstNameInitial", false)
        ) {
            for (i in name.indices) {
                if (name[i] == '.') return name.substring(i + 2)
            }
        }
        return name
    }

    public fun formatRoomName(room: String): String {
        if (!preference.getBoolean(
                "showLeadingZerosInRooms",
                false
            ) && !room.startsWith("TH") && room[1] == '0'
        ) {
            return room[0].toString() + room[2];
        }
        return room
    }

}