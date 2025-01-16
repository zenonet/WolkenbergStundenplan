package de.zenonet.stundenplan.wear.complications

import android.content.Context
import android.preference.PreferenceManager
import androidx.wear.complications.data.ComplicationData
import androidx.wear.complications.data.ComplicationType
import androidx.wear.complications.data.EmptyComplicationData
import androidx.wear.complications.data.LongTextComplicationData
import androidx.wear.complications.data.NoDataComplicationData
import androidx.wear.complications.data.NotConfiguredComplicationData
import androidx.wear.complications.data.PlainComplicationText
import androidx.wear.complications.data.RangedValueComplicationData
import androidx.wear.complications.data.ShortTextComplicationData
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager

fun androidx.wear.complications.ComplicationRequest.getEmptyData() : ComplicationData{
    return when(this.complicationType){
        ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
            text = emptyComplicationText,
            contentDescription = emptyComplicationText
        ).build()
        ComplicationType.NO_DATA -> NoDataComplicationData()
        ComplicationType.EMPTY -> EmptyComplicationData()
        ComplicationType.NOT_CONFIGURED -> NotConfiguredComplicationData()
        ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(emptyComplicationText, emptyComplicationText).build()
        ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(0f, 0f, 1f, emptyComplicationText).build()
        ComplicationType.MONOCHROMATIC_IMAGE -> TODO()
        ComplicationType.SMALL_IMAGE -> TODO()
        ComplicationType.PHOTO_IMAGE -> TODO()
        ComplicationType.NO_PERMISSION -> TODO()
    }
}

fun getTimeTable(context: Context): TimeTable?{
    val isPreview = PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application)
        .getBoolean("showPreview", false)

    val timetable: TimeTable = if(isPreview) {
        Utils.getPreviewTimeTable(StundenplanApplication.application)
    }else{
        val manager = TimeTableManager()
        manager.init(context)
        try {
            manager.getCurrentTimeTable()
        }catch (e: Exception){
            return null
        }
    }
    return timetable
}

fun getCurrentLesson(context:Context):Lesson?{
    val dayOfWeek = Timing.getCurrentDayOfWeek()
    val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime().plusMinutes(6))
    if(dayOfWeek > 4 || dayOfWeek < 0 || currentPeriod == -1) {
        return null
    }

    val timetable = getTimeTable(context)
    if (timetable == null || currentPeriod >= timetable.Lessons[dayOfWeek].size) {
        return null
    }
    return timetable.Lessons[dayOfWeek][currentPeriod]
}

private val emptyComplicationText = PlainComplicationText.Builder("Frei").build()