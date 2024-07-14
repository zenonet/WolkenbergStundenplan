package de.zenonet.stundenplan.wear.presentation

import android.icu.util.Calendar
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

import java.time.Instant
import java.time.temporal.ChronoUnit

class WearTimeTableViewModel(val startLoginActivity: () -> Unit) : ViewModel() {
    var isPreview by mutableStateOf(false)
    val formatter = Formatter(StundenplanApplication.application)

    private var timeTableManager: TimeTableManager? = null;
    var weekOfYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.WEEK_OF_YEAR))
    val currentWeekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)

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
        weekOfYear++
        loadTimetable()
    }

    fun previousWeek() {
        weekOfYear--
        loadTimetable()
    }

    fun backToCurrentWeek() {
        weekOfYear = currentWeekOfYear
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
            Utils.LOG_TAG,
            "Time from app start to timetable load start: ${
                ChronoUnit.MILLIS.between(
                    StundenplanApplication.applicationEntrypointInstant!!,
                    Instant.now()
                )
            }ms"
        )

        timeTableManager!!.getTimeTableAsyncWithAdjustments(weekOfYear) {
            if(it == null) {
                Log.i(Utils.LOG_TAG, "Got null timetable")
                return@getTimeTableAsyncWithAdjustments
            }
            Log.i(
                Utils.LOG_TAG,
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