/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package de.zenonet.stundenplan.wear.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults.chipColors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.common.R as CommonR
import de.zenonet.stundenplan.wear.presentation.theme.StundenplanTheme
import kotlinx.coroutines.launch
import java.time.LocalTime

class WearTimeTableViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        if (!PreferenceManager.getDefaultSharedPreferences(this).contains("refreshToken")) {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            TimeTable(this)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeTable(context: Context) {
    StundenplanTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {


            var timeTableManager: TimeTableManager? by remember {
                mutableStateOf(null)
            }

            val pagerState = rememberPagerState(pageCount = {
                5
            })

            var timeTable: TimeTable? by remember {
                mutableStateOf(null)
            }

            LaunchedEffect(key1 = null) {
                launch {
                    Utils.CachePath = context.cacheDir.path
                    timeTableManager = TimeTableManager();
                    timeTableManager!!.init(context)

                    // fetch time table
                    timeTableManager?.getTimeTableAsyncWithAdjustments {
                        timeTable = it;
                    }
                }
            }

            val weekDays = arrayOf(
                "Montag",
                "Dienstag",
                "Mittwoch",
                "Donnerstag",
                "Freitag",
            )
            // Display the shown weekday using a TimeText with a fake TimeSource
            TimeText(timeSource = WeekDayTimeSource(weekDays[pagerState.currentPage]))

            val dayOfWeek = Timing.getCurrentDayOfWeek()

            HorizontalPager(
                state = pagerState,
                Modifier.fillMaxSize(),
            ) { day ->

                val listState = rememberScalingLazyListState()
                ScalingLazyColumn(
                    Modifier
                        .padding(20.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    state = listState,
                ) {
                    if (timeTable == null) return@ScalingLazyColumn;

                    val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime())

                    items(timeTable!!.Lessons[day].size) { period ->
                        LessonView(
                            lesson = timeTable!!.Lessons[day][period],
                            currentPeriod == period,
                            period + 1
                        )

                        // Scroll to the current period
                        LaunchedEffect(null) {
                            // Scroll if the school-day is not yet over, this column show the current day and
                            // this lesson view shows the first lesson (to only scroll once)
                            if (currentPeriod < timeTable!!.Lessons[day].size && dayOfWeek == day && period == 0) {
                                listState.scrollToItem(currentPeriod)
                            }
                        }
                    }
                }

            }

            LaunchedEffect(null) {
                // Scroll to the current day
                if (dayOfWeek > 4) return@LaunchedEffect
                pagerState.scrollToPage(dayOfWeek)
            }
        }
    }
}


@Composable
fun getBackgroundColorForLesson(lesson: Lesson): Color {
    if (!lesson.isTakingPlace) return colorResource(CommonR.color.cancelled_lesson)

    return when (lesson.Type) {
        LessonType.Substitution -> colorResource(CommonR.color.substituted_lesson)
        LessonType.RoomSubstitution -> colorResource(CommonR.color.room_substituted_lesson)
        else -> {
            colorResource(CommonR.color.regular_lesson)
        }
    }
}

@Composable
fun LessonView(lesson: Lesson, isCurrent: Boolean = false, displayPeriod: Int) {
    val chipColors = chipColors(
        backgroundColor = getBackgroundColorForLesson(lesson),
        contentColor = Color.Black
    )

    Chip(
        label = {
            val subject = lesson.SubjectShortName
            Text("$displayPeriod. $subject")
            Spacer(Modifier.weight(1f))
            Text(text = lesson.Room, textAlign = TextAlign.Right)
        },
        onClick = { },
        secondaryLabel = { Text("Mit ${lesson.Teacher}") },
        modifier = Modifier.fillMaxWidth(),
        colors = chipColors
    )
}

/*
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview2() {
    TimeTable(null)
}*/

class WeekDayTimeSource(weekDay: String) : TimeSource {
    private val _weekDay = weekDay

    override val currentTime: String
        @Composable
        get() = _weekDay;
}
