package de.zenonet.stundenplan

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.common.quoteOfTheDay.QuoteProvider
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class NonCrucialViewModel(
    val ttm: TimeTableManager? = null,
    private val quote: Quote? = null
) : ViewModel() {

    private val _quoteOfTheDay = MutableStateFlow<Quote?>(Quote())
    val quoteOfTheDay: StateFlow<Quote?> = _quoteOfTheDay.asStateFlow()

    private val _currentTimeTable = MutableStateFlow<TimeTable?>(null)
    val currentTimeTable: StateFlow<TimeTable?> = _currentTimeTable.asStateFlow()

    var stairCasesUsed by mutableIntStateOf(-1)

    private val quoteProvider: QuoteProvider = QuoteProvider()


    init {
        if (quote != null) {
            _quoteOfTheDay.value = quote
        }
    }

    private var loadingTimeTable = false;
    fun loadTimeTable() {
        if (loadingTimeTable || currentTimeTable.value != null || ttm == null) return

        loadingTimeTable = true
        viewModelScope.launch {

            val tt = withContext(Dispatchers.IO) {
                ttm.getCurrentTimeTable()
            }
            _currentTimeTable.value = tt
        }
        loadingTimeTable = false
    }

    fun analyzeStaircaseUsage() {
        loadTimeTable()
        if (currentTimeTable.value == null) return

        val tt: TimeTable = currentTimeTable.value!!

        val dayOfWeek = Timing.getCurrentDayOfWeek()
        // Analyze the current day
        var lastHeight = 0
        var stairCases = 0
        for (lesson in tt.Lessons[dayOfWeek]) {

            val c: Char = if(lesson != null) lesson.Room[0] else 'B'

            val height = when(c){
                'A' -> -1
                'B' -> 0
                'C' -> 1
                'D' -> 2
                'E' -> 3
                'T' -> 0
                else -> throw Exception("Unknown room ${lesson.Room}")
            }
            stairCases += abs(height-lastHeight)
            lastHeight = height
        }
        stairCases += abs(lastHeight) // Go to ground level to leave the building
        stairCasesUsed = stairCases
    }

    fun loadQuoteOfTheDay() {
        if (quote != null) return

        viewModelScope.launch {

            val q = withContext(Dispatchers.IO) {
                quoteProvider.getQuoteOfTheDay()
            }
            _quoteOfTheDay.value = q
            Log.i(LogTags.Debug, "Assigned quote to state")

        }
    }
}