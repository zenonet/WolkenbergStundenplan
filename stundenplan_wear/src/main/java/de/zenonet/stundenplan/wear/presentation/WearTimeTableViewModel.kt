package de.zenonet.stundenplan.wear.presentation

import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.flow.Flow

import java.time.Instant
import java.time.temporal.ChronoUnit

class WearTimeTableViewModel(val startLoginActivity: () -> Unit) : ViewModel() {
    var isPreview by mutableStateOf(false)
    val formatter = Formatter(StundenplanApplication.application)

    private var timeTableManager: TimeTableManager? = null;
    val currentWeek = Timing.getRelevantWeekOfYear()
    var selectedWeek by mutableStateOf(currentWeek)

    private val _timeTable: MutableLiveData<TimeTable?> = MutableLiveData<TimeTable?> (null)
    val timeTable: Flow<TimeTable?> = _timeTable.asFlow()
    var isLoading by mutableStateOf(true)
    var timeTableDirect: TimeTable? = null

    init {
        isPreview =
            PreferenceManager.getDefaultSharedPreferences(StundenplanApplication.application)
                .getBoolean("showPreview", false)
    }

    fun nextWeek() {
        selectedWeek = selectedWeek.succeedingWeek
        loadTimetable()
    }

    fun previousWeek() {
        selectedWeek = selectedWeek.preceedingWeek
        loadTimetable()
    }

    fun backToCurrentWeek() {
        selectedWeek = currentWeek
        loadTimetable()
    }
    fun loadTimetable() {
        isLoading = true

        if (isPreview) {
            _timeTable.postValue(Utils.getPreviewTimeTable(StundenplanApplication.application))
            return
        }

        if (timeTableManager == null) {
            timeTableManager = TimeTableManager()
            timeTableManager!!.init(StundenplanApplication.application)
        }

        Log.i(
            LogTags.Timing,
            "Time from app start to timetable load start: ${
                ChronoUnit.MILLIS.between(
                    StundenplanApplication.applicationEntrypointInstant!!,
                    Instant.now()
                )
            }ms"
        )

        timeTableManager!!.getTimeTableAsyncWithAdjustments(selectedWeek) {
            if(it == null) {
                Log.i(LogTags.Debug, "Got null timetable")
                return@getTimeTableAsyncWithAdjustments
            }
            Log.i(
                LogTags.Timing,
                "Got timetable from ${it.source}${if (it.isCacheStateConfirmed) " (confirmed)" else ""} (${
                    ChronoUnit.MILLIS.between(
                        StundenplanApplication.applicationEntrypointInstant!!,
                        Instant.now()
                    )
                }ms after app start)"
            )
            timeTableDirect = it
            _timeTable.postValue(it)
            isLoading = false
        }
    }
}