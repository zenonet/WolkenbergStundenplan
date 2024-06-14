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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults.chipBorder
import androidx.wear.compose.material.ChipDefaults.chipColors
import androidx.wear.compose.material.ChipDefaults.outlinedChipBorder
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.common.R as CommonR
import de.zenonet.stundenplan.wear.presentation.theme.StundenplanTheme
import kotlinx.coroutines.launch

class WearTimeTableViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .contains("refreshToken") && !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("showPreview", false)
        ) {
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
                if (PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("showPreview", false)
                ) {
                    timeTable = Utils.getPreviewTimeTable(context)
                    return@launch
                }
                Utils.CachePath = context.cacheDir.path
                timeTableManager = TimeTableManager();
                timeTableManager!!.init(context)

                // fetch time table
                timeTableManager?.getCurrentTimeTableAsyncWithAdjustments {
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


        val dayOfWeek = Timing.getCurrentDayOfWeek()
        val formatter = Formatter(context)
        HorizontalPager(
            state = pagerState,
            Modifier.fillMaxSize(),
        ) { day ->
            val listState = rememberScalingLazyListState()
            Scaffold(
                positionIndicator = {
                    PositionIndicator(scalingLazyListState = listState)
                }
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(0.dp, 10.dp, 0.dp, 0.dp)
                ) {

                    Text(
                        weekDays[day],
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(0.dp, 2.dp, 0.dp, 0.dp), textAlign = TextAlign.Center
                    )

                    var hasScrolledToCurrentPeriod by remember { mutableStateOf(false) }


                    ScalingLazyColumn(
                        Modifier
                            .padding(10.dp, 0.dp, 10.dp, 0.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        state = listState,
                    ) {

                        if (timeTable == null) return@ScalingLazyColumn;

                        val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime())

                        items(timeTable!!.Lessons[day].size) { period ->
                            if (timeTable!!.Lessons[day][period] == null) {
                                Spacer(Modifier.height(15.dp))
                                return@items
                            }

                            LessonView(
                                lesson = timeTable!!.Lessons[day][period],
                                formatter = formatter,
                                day == dayOfWeek && currentPeriod == period,
                                period + 1
                            )

                            // Scroll to the current period
                            LaunchedEffect(null) {
                                // Scroll if the school-day is not yet over, this column show the current day and
                                // this lesson view shows the first lesson (to only scroll once)
                                if (!hasScrolledToCurrentPeriod && currentPeriod < timeTable!!.Lessons[day].size && dayOfWeek == day && period == 0) {
                                    listState.scrollToItem(currentPeriod)
                                    hasScrolledToCurrentPeriod = true
                                }
                            }
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
fun LessonView(
    lesson: Lesson,
    formatter: Formatter,
    isCurrent: Boolean = false,
    displayPeriod: Int
) {
    val chipColors = chipColors(
        backgroundColor = getBackgroundColorForLesson(lesson),
        contentColor = Color.Black
    )

    Chip(
        label = {
            val subject = lesson.SubjectShortName
            Text("$displayPeriod. $subject")
            Spacer(Modifier.weight(1f))
            Text(
                text = formatter.formatRoomName(lesson.Room),
                textAlign = TextAlign.Right,
                fontSize = 14.nSp
            )
        },
        onClick = { },
        secondaryLabel = {
            Text("Mit ${formatter.formatTeacherName(lesson.Teacher)}", fontSize = 12.nSp)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = chipColors,
        border = if (isCurrent) outlinedChipBorder(
            borderWidth = 2.dp,
        ) else chipBorder(),
    )
}


val Int.nSp
    @Composable
    get() = (this / LocalDensity.current.fontScale).sp

/*
@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview2() {
    TimeTable(null)
}*/