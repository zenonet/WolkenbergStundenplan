package de.zenonet.stundenplan.nonCrucialUi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewManagerFactory
import de.zenonet.stundenplan.common.HomeworkManager
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.common.quoteOfTheDay.QuoteProvider
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.Post
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.homework.HomeworkEditorActivity
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalField
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class NonCrucialViewModel(
    val ttm: TimeTableManager? = null,
    private val quote: Quote? = null,
    val previewTimeTable: TimeTable? = null
) : ViewModel() {

    private val _currentTimeTable = MutableStateFlow<TimeTable?>(null)
    val currentTimeTable: StateFlow<TimeTable?> = _currentTimeTable.asStateFlow()

    //region quoteOfTheDay
    private val quoteProvider: QuoteProvider = QuoteProvider()
    private val _quoteOfTheDay = MutableStateFlow<Quote?>(Quote())
    val quoteOfTheDay: StateFlow<Quote?> = _quoteOfTheDay.asStateFlow()
    suspend fun loadQuoteOfTheDay() {
        if (quote != null) return
        val q = withContext(Dispatchers.IO) {
            try {
                quoteProvider.getQuoteOfTheDay()
            } catch (_: Exception) {
                null
            }
        }
        if (q != null) {
            _quoteOfTheDay.value = q
            Log.i(LogTags.Debug, "Assigned quote to state")
        }
    }
    //endregion


    private var loadingTimeTable = false;
    private var timeTableLoadingJob: Job? = null;

    suspend fun loadTimeTableAsync(
        week: Int = Timing.getRelevantWeekOfYear()
    ): Deferred<TimeTable?> = coroutineScope {
        async {
            if (ttm == null) return@async previewTimeTable

            val tt = withContext(Dispatchers.IO) {
                try {
                    ttm.login()
                    ttm.getTimeTableForWeek(week)
                } catch (e: Exception) {
                    return@withContext null
                }
            }
            if (week == Timing.getRelevantWeekOfYear()) {
                _currentTimeTable.value = tt
            }

            tt
        }
    }

    suspend fun loadTimeTable(): Job {
        if (timeTableLoadingJob != null) return timeTableLoadingJob!!;

        return coroutineScope {
            timeTableLoadingJob = launch {

                if (currentTimeTable.value != null) return@launch

                loadingTimeTable = true

                if (ttm != null) {
                    val tt = withContext(Dispatchers.IO) {
                        ttm.login()
                        ttm.getCurrentTimeTable()
                    }
                    _currentTimeTable.value = tt
                } else {
                    _currentTimeTable.value = previewTimeTable
                }
                loadingTimeTable = false
                return@launch
            }
            timeTableLoadingJob!!
        }
    }

    private val _posts = MutableStateFlow<Array<Post>?>(null)
    val posts: StateFlow<Array<Post>?> = _posts.asStateFlow()
    suspend fun loadPosts() {
        val p = withContext(Dispatchers.IO) {
            ttm?.getPosts()
        }
        _posts.value = p
    }

    //region staircase analysis
    var stairCasesUsedToday by mutableIntStateOf(-1)
    var stairCasesUsedThisWeek by mutableIntStateOf(-1)
    var stairCaseAnalysisCompleted by mutableStateOf(false)
    suspend fun analyzeStaircaseUsage() {
        if (currentTimeTable.value == null) loadTimeTableAsync().await()

        if (currentTimeTable.value == null) return

        try {

            stairCasesUsedToday = calculateStaircasesUsedOnDay(Timing.getCurrentDayOfWeek())

            // calculate for week
            stairCasesUsedThisWeek = 0
            for (day in 0..4) {
                stairCasesUsedThisWeek += calculateStaircasesUsedOnDay(day)
            }
            stairCaseAnalysisCompleted = true
        } catch (e: Exception) {
            Log.e(LogTags.TimeTableAnalysis, e.stackTraceToString())
        }
    }

    private fun calculateStaircasesUsedOnDay(dayOfWeek: Int): Int {
        val tt: TimeTable = currentTimeTable.value!!

        if (dayOfWeek > 4 || dayOfWeek < 0) return 0
        // Analyze the current day
        var lastHeight = 0
        var stairCases = 0
        for ((period, lesson) in tt.Lessons[dayOfWeek].withIndex()) {

            val height = if (!Lesson.doesTakePlace(lesson)) 0
            else when (lesson.Room[0]) {
                'A' -> -1
                'B' -> 0
                'C' -> 1
                'D' -> 2
                'E' -> 3
                'T' -> 0
                else -> throw Exception("Unknown room ${lesson.Room}")
            }

            // Assume the user goes to 0th floor after the second and the fourth period (breaks)
            if (period == 2 || period == 4) {
                stairCases += abs(lastHeight)
                lastHeight = 0
            }

            stairCases += abs(height - lastHeight)
            lastHeight = height
        }
        stairCases += abs(lastHeight) // Go to ground level to leave the building
        return stairCases
    }
    //endregion

    //region review requests
    var showReviewRequest by mutableStateOf(true)
    suspend fun askForPlayStoreReview(context: Context) {
        if (context !is Activity) return

        try {
            val manager = ReviewManagerFactory.create(context)
            val request = manager.requestReviewFlow()
            val reviewInfo = request.await()
            val flow = manager.launchReviewFlow(context, reviewInfo)
            flow.await()
            showReviewRequest = false
        } catch (_: Exception) {

        }
    }

    //endregion

    //region currentLessonInfo
    var currentPeriod by mutableIntStateOf(-1)
    var startTime: LocalTime? by mutableStateOf(LocalTime.MIN)
    var endTime: LocalTime? by mutableStateOf(LocalTime.MIN)
    var currentTime: LocalTime by mutableStateOf(Timing.getCurrentTime())
    var isBreak: Boolean by mutableStateOf(false)
    var lessonProgress: Int by mutableIntStateOf(0)
    var currentLesson: Lesson? by mutableStateOf(null)

    var isFreeSection: Boolean by mutableStateOf(false)
    var freeSectionStartTime: LocalTime? by mutableStateOf(null)
    var freeSectionEndTime: LocalTime? by mutableStateOf(null)
    var freeSectionProgress: Int by mutableIntStateOf(0)
    var nextActualLesson: Lesson? by mutableStateOf(null)

    fun startRegularDataRecalculation() {
        // Update progress regularly (this is implemented here because it's the right place for it according to this: https://developer.android.com/topic/libraries/architecture/coroutines#viewmodelscope)
        viewModelScope.launch {
            while (true) {
                generateCurrentLessonInfoData()
                delay(1000 * 27) // Updating every 27 seconds means that every percent of a 45 minute lesson will be shown (1%*45min = 45min/100 = 2700s/100 = 27s)
            }
        }
    }

    fun generateCurrentLessonInfoData() {
        Log.i(LogTags.UI, "Recalculating data for current lesson info...")
        currentTime = Timing.getCurrentTime()
        currentPeriod = Utils.getCurrentPeriod(currentTime)
        // This fixes all possible indexing problems because CurrentLessonInfo() just won't compose when vm.currentPeriod == -1
        if (currentPeriod == -1) return

        val pair = Utils.getStartAndEndTimeOfPeriod(currentPeriod)
        startTime = pair?.first
        endTime = pair?.second

        if (startTime != null && endTime != null) {
            isBreak = startTime!!.isAfter(currentTime)

            if (isBreak && currentPeriod > 0) {
                endTime = startTime

                val pairOfLessonBefore = Utils.getStartAndEndTimeOfPeriod(currentPeriod - 1)
                startTime = pairOfLessonBefore.second
            }

            lessonProgress = calculateProgress(startTime!!, endTime!!, currentTime)
        }

        if (currentTimeTable.value != null) {
            val timeTable = currentTimeTable.value!!
            val day = timeTable.Lessons?.get(Timing.getCurrentDayOfWeek()) ?: return

            currentLesson = if (day.size > currentPeriod) day[currentPeriod] else null

            // Handle free sections
            if (currentPeriod < day.size && !Lesson.doesTakePlace(currentLesson)) {

                var nextPeriod = currentPeriod + 1
                while (nextPeriod < day.size && !Lesson.doesTakePlace(day[nextPeriod])) nextPeriod++
                if (nextPeriod == day.size) return

                // Last period meaning the last period before the current free section began
                var lastPeriod = currentPeriod
                while (lastPeriod > 0 && !Lesson.doesTakePlace(day[lastPeriod])) lastPeriod--
                if (lastPeriod == -1) return

                isFreeSection = true
                nextActualLesson = day[nextPeriod]

                freeSectionStartTime = Utils.getStartAndEndTimeOfPeriod(lastPeriod).second
                freeSectionEndTime = Utils.getStartAndEndTimeOfPeriod(nextPeriod).first

                freeSectionProgress =
                    calculateProgress(freeSectionStartTime!!, freeSectionEndTime!!, currentTime)
            }
        }
    }

    private fun calculateProgress(start: LocalTime, end: LocalTime, current: LocalTime): Int {
        val totalLessonSeconds = end.toSecondOfDay() - start.toSecondOfDay()
        val progressInSeconds = current.toSecondOfDay() - start.toSecondOfDay()
        return (progressInSeconds.toFloat() / totalLessonSeconds * 100).roundToInt()
    }

    //endregion


    //region app update notices
    var isAppUpdateAvailable by mutableStateOf(false)
    private var appUpdateManager: AppUpdateManager? = null

    suspend fun checkForAppUpdates(context: Context) {

        try {
            appUpdateManager = AppUpdateManagerFactory.create(context)
            val appUpdateInfo = appUpdateManager!!.appUpdateInfo.await()
            isAppUpdateAvailable =
                appUpdateInfo!!.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        } catch (_: Exception) {
            isAppUpdateAvailable = false
        }
    }

    fun updateAppNow(context: Activity) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                )
            )

        } catch (e: Exception) {
            isAppUpdateAvailable = false
        }
    }

    fun dontUpdateAppNow() {
        isAppUpdateAvailable = false
    }
    //endregion

    //region homework

    var homeworkEntries: List<HomeworkEntry>? by mutableStateOf(null)
    suspend fun loadHomework() {
        val currentWeek = Timing.getRelevantWeekOfYear()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val dayOfWeek = Timing.getCurrentDayOfWeek()
        val period = Utils.getCurrentPeriod(Timing.getCurrentTime())
        // load homework for these weeks
        val weekFields = WeekFields.of(Locale.GERMANY)

        val timeTables =
            (currentWeek..currentWeek + 1).map { loadTimeTableAsync(it) }.awaitAll().filterNotNull()
                .also {
                    it.forEachIndexed { week, tt ->
                        HomeworkManager.populateTimeTable(
                            year,
                            currentWeek + week,
                            tt
                        )
                    }
                };
        Log.i(LogTags.Debug, timeTables[0].Lessons[4][7].HasHomeworkAttached.toString())
        homeworkEntries = timeTables.flatMapIndexed { weekOffset, tt ->
            tt.Lessons.flatMapIndexed { dayIndex, day ->
                if(dayIndex < dayOfWeek && weekOffset == 0) return@flatMapIndexed emptyList()
                Log.i(LogTags.Debug, "Flattening day $dayIndex")
                day.filterIndexed { li, l ->
                    Log.i(LogTags.Debug, "lesson index:$li, dayIndex: $dayIndex")

                    l != null && l.HasHomeworkAttached
                }
                    .distinctBy { it.SubjectShortName }.map { l ->
                        HomeworkEntry(
                            HomeworkManager.getNoteFor(
                                year,
                                currentWeek + weekOffset,
                                dayIndex,
                                l.SubjectShortName.hashCode()
                            ), l, LocalDate.of(year, 1, 1).with(
                                weekFields.weekOfYear(), (currentWeek + weekOffset).toLong()
                            ).with(weekFields.dayOfWeek(), 1).plusDays(dayIndex.toLong())
                        )
                    }
                }
        }
    }

    fun openHomeworkEditor(entry: HomeworkEntry, context: Context) {
        val intent = Intent(context, HomeworkEditorActivity::class.java)
            .putExtra("week", entry.day.get(WeekFields.of(Locale.GERMANY).weekOfYear()))
            .putExtra("dayOfWeek", entry.day.dayOfWeek.value - 1)
            .putExtra(
                "subjectAbbreviationHash",
                entry.lesson.SubjectShortName.hashCode()
            )
        context.startActivity(intent)
    }

    //endregion

    init {
        if (quote != null) {
            _quoteOfTheDay.value = quote
        }
    }
}

data class HomeworkEntry(
    val text: String,
    val lesson: Lesson,
    val day: LocalDate
);