package de.zenonet.stundenplan.wear.presentation

import android.preference.PreferenceManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem

class MyDataChangedListener(private val loginActivity: LoginActivity) :
    DataClient.OnDataChangedListener {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == "/refreshtoken") {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val refreshToken = dataMap.getString("refreshToken")
                    val showPreview = dataMap.getBoolean("showPreview")
                    // Use the refresh token as needed

                    PreferenceManager.getDefaultSharedPreferences(loginActivity).edit()
                        .putBoolean("showPreview", showPreview).apply()
                    if (!showPreview)
                        PreferenceManager.getDefaultSharedPreferences(loginActivity).edit()
                            .putString("refreshToken", refreshToken).apply()

                    loginActivity.loginSucceeded();
                }
            }
        }
    }
}