package de.zenonet.stundenplan.wear.presentation

import android.icu.util.Calendar
import android.preference.PreferenceManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearTimeTableViewModel(val startLoginActivity: () -> Unit) : ViewModel() {
    var timeTable: TimeTable? by mutableStateOf(null)
    var isPreview by mutableStateOf(false)
    val formatter = Formatter(StundenplanApplication.application)

    private var timeTableManager: TimeTableManager? = null;
    var weekOfYear by mutableIntStateOf(Calendar.getInstance().get(Calendar.WEEK_OF_YEAR))
    val currentWeekOfYear = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)

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
        viewModelScope.launch {
            if (isPreview) {
                timeTable = Utils.getPreviewTimeTable(StundenplanApplication.application)
                return@launch
            }

            if (timeTableManager == null) {
                timeTableManager = TimeTableManager()
                timeTableManager!!.init(StundenplanApplication.application)
            }
            val tt = withContext(Dispatchers.IO) {
                timeTableManager!!.login()
                timeTableManager!!.getTimeTableForWeek(weekOfYear);
            }
            withContext(Dispatchers.Default) {
                timeTable = tt
            }

            /*timeTableManager!!.getTimeTableAsyncWithAdjustments(weekOfYear) {
                Log.i(Utils.LOG_TAG, "Got timetable!")
                withContext(Dispatchers.Main){

                }
                timeTable = it
            }*/
        }
    }
}