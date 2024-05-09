package de.zenonet.stundenplan.wear.presentation.complications


import android.util.Log
import androidx.wear.complications.ComplicationProviderService
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import java.time.LocalTime
import java.util.Calendar

/**
 * Example watch face complication data source provides a number that can be incremented on tap.
 *
 * Note: This class uses the suspending variation of complication data source service to support
 * async calls to the data layer, that is, to the DataStore saving the persistent values.
 */
class CustomComplicationDataSourceService : ComplicationProviderService() {


    override fun getPreviewData(type: ComplicationType): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text = "Geo")
                .build(),
            contentDescription = PlainComplicationText.Builder(text = "Aktuelles Fach")
                .build()
        )
            .build()
    }

    /*
     * Called when the complication needs updated data from your data source. There are four
     * scenarios when this will happen:
     *
     *   1. An active watch face complication is changed to use this data source
     *   2. A complication using this data source becomes active
     *   3. The period of time you specified in the manifest has elapsed (UPDATE_PERIOD_SECONDS)
     *   4. You triggered an update from your own class via the
     *       ComplicationDataSourceUpdateRequester.requestUpdate method.
     */
    private fun getComplicationDataAsync(
        request: androidx.wear.complications.ComplicationRequest,
        callback: ((ComplicationData?) -> Unit)
    ) {
        Thread {

            val dayOfWeek = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) % 7
            val currentPeriod = Utils.getCurrentPeriod(LocalTime.now())
            if(dayOfWeek > 4 || currentPeriod == -1) {
                callback(null)
                return@Thread
            }

            // Retrieves your data, in this case, we grab an incrementing number from Datastore.
            val manager = TimeTableManager()
            manager.init(this)

            val timetable = manager.getCurrentTimeTable()

            val text = timetable.Lessons[dayOfWeek][currentPeriod].SubjectShortName;//String.format(Locale.getDefault(), "%d", timetable.Lessons[dayOfWeek][currentPeriod].SubjectShortName)

            Log.i(Utils.LOG_TAG, "Setting complication value '$text'")
            val data = when (request.complicationType) {

                ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(text = text).build(),
                    contentDescription = PlainComplicationText
                        .Builder(text = "Aktuelles Fach").build()
                )
                    .build()

                else -> {
                    if (Log.isLoggable(TAG, Log.WARN)) {
                        Log.w(TAG, "Unexpected complication type ${request.complicationType}")
                    }
                    null
                }
            }
            callback.invoke(data)
        }.start()
    }

    /*
     * Called when the complication has been deactivated.
     */
    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        Log.d(TAG, "onComplicationDeactivated(): $complicationInstanceId")
    }

    override fun onComplicationRequest(
        request: androidx.wear.complications.ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        getComplicationDataAsync(request) {
            listener.onComplicationData(it)
        }
    }


    companion object {
        private const val TAG = "CompDataSourceService"
    }
}
