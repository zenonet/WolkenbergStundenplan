package de.zenonet.stundenplan.wear.presentation

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults.chipBorder
import androidx.wear.compose.material.ChipDefaults.chipColors
import androidx.wear.compose.material.ChipDefaults.outlinedChipBorder
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.StatisticsManager
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.wear.BuildConfig
import de.zenonet.stundenplan.wear.presentation.theme.StundenplanTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import de.zenonet.stundenplan.common.R as CommonR

class WearTimeTableViewActivity : ComponentActivity() {
    var viewmodel: WearTimeTableViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)


        if (!PreferenceManager.getDefaultSharedPreferences(this)
                .contains("refreshToken") && !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("showPreview", false)
        ) {
            startLoginActivity()
            return
        }

        setTheme(android.R.style.Theme_DeviceDefault)
        val vmTime = measureTime {
            viewmodel = WearTimeTableViewModel {
                startLoginActivity()
            }
            viewmodel!!.loadTimetable()
        }

        // Register network available listener
        val cm = this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager;
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewmodel?.checkForUpdates()
            }

        })

        Log.i(
            LogTags.UI,
            "Time to setting content ${StundenplanApplication.getMillisSinceAppStart()}ms"
        )
        setContent {
            TimeTable(viewmodel!!)
        }
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()

        viewmodel?.checkForUpdates()
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeTable(viewModel: WearTimeTableViewModel) {
    StundenplanTheme {
        val pagerState = rememberPagerState(
            pageCount = {
                6 // the menu + 5 weekdays
            },
            initialPage = 1
        )

        val dayOfWeek = Timing.getCurrentDayOfWeek()

        val timeTable: TimeTable? by viewModel.timeTable.collectAsState(viewModel.timeTableDirect)

        HorizontalPager(
            state = pagerState,
            Modifier.fillMaxSize(),
        ) { page ->

            if (page == 0) {
                Menu(viewModel);
                return@HorizontalPager
            }

            val day = page - 1
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
                        Utils.getWordForDayOfWeek(day),
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(0.dp, 3.dp, 0.dp, 0.dp), textAlign = TextAlign.Center
                    )

                    var hasScrolledToCurrentPeriod by remember { mutableStateOf(false) }

                    ScalingLazyColumn(
                        Modifier
                            .padding(10.dp, 2.dp, 10.dp, 0.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        state = listState,
                    ) {

                        if (timeTable != null && !timeTable!!.Lessons[day].isEmpty() && !timeTable!!.Lessons[day].all { it == null || !it.isTakingPlace }) {
                            val currentPeriod = Utils.getCurrentPeriod(Timing.getCurrentTime())
                            //items(viewModel.timeTable.value!!.Lessons[day].size) { period ->
                            items(8) { period ->
                                // val timeTable = if (viewModel.timeTable.value == null) viewModel.timeTableDirect else viewModel.timeTable.value
                                if (timeTable == null && viewModel.timeTableDirect == null) return@items

                                if (timeTable!!.Lessons[day].size <= period)
                                    return@items

                                if (timeTable!!.Lessons[day][period] == null) {
                                    Spacer(Modifier.height(15.dp))
                                    return@items
                                }

                                LessonView(
                                    lesson = timeTable!!.Lessons[day][period],
                                    formatter = viewModel.formatter,
                                    day == dayOfWeek && currentPeriod == period && viewModel.selectedWeek == viewModel.currentWeek,
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
                        } else if(timeTable == null){
                            item{
                                Text("Fehler",
                                    color = colorResource(id = CommonR.color.substituted_lesson),
                                    fontSize = 20.sp
                                )
                            }
                        }else{
                            item {
                                Text("Frei",
                                    color = colorResource(id = CommonR.color.cancelled_lesson),
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                    if (timeTable != null || viewModel.timeTableDirect != null) {
                        StatisticsManager.reportTimetableTime(StundenplanApplication.getMillisSinceAppStart())
                    }

                }

            }
        }

        LaunchedEffect(null) {
            // Scroll to the current day
            if (dayOfWeek == -1 || dayOfWeek > 4) return@LaunchedEffect
            pagerState.scrollToPage(dayOfWeek + 1)
        }
    }
}

@Composable
fun Menu(viewModel: WearTimeTableViewModel, modifier: Modifier = Modifier) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        val timeTable by viewModel.timeTable.collectAsState(viewModel.timeTableDirect)
        ScalingLazyColumn(
            Modifier
                .padding(10.dp, 0.dp, 10.dp, 0.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            state = listState,
        ) {

            item {
                Text(
                    "${viewModel.selectedWeek.WeekOfYear}. Woche",
                    Modifier
                        .fillMaxWidth()
                        .padding(5.dp), textAlign = TextAlign.Center
                )
            }
            item {
                val text = Utils.getSourceText(timeTable, viewModel.isLoading)
                Text(
                    text,
                    Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                 )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(5.dp), horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { viewModel.previousWeek() },
                        Modifier.padding(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Vorherige Woche anzeigen"
                        )
                    }

                    Button(
                        onClick = { viewModel.nextWeek() },
                        Modifier.padding(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Nächste Woche anzeigen"
                        )
                    }
                }
            }
            item {
                AnimatedVisibility(viewModel.selectedWeek != viewModel.currentWeek) {
                    Button(
                        onClick = {
                            viewModel.backToCurrentWeek()
                        },
                        Modifier
                            .fillMaxWidth()
                            .padding(5.dp, 0.dp, 5.dp, 0.dp)
                    ) {
                        Text("Aktuelle Woche")
                    }
                }
            }
            item {
                Spacer(Modifier.height(30.dp))
            }
            item {
                if (viewModel.isPreview) {
                    Button(
                        onClick = {
                            viewModel.startLoginActivity()
                        },
                        Modifier
                            .fillMaxWidth()
                            .padding(5.dp, 0.dp, 5.dp, 0.dp)
                    ) {
                        Text("Login")
                    }
                }
            }

            item {
                Text(
                    "v${BuildConfig.VERSION_NAME} (id: ${BuildConfig.VERSION_CODE})",
                    fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
fun getBackgroundColorForLesson(lesson: Lesson): Color {
    if (!lesson.isTakingPlace/* && lesson.Type != LessonType.Assignment*/) return colorResource(CommonR.color.cancelled_lesson)

    return when (lesson.Type) {
        LessonType.Substitution -> colorResource(CommonR.color.substituted_lesson)
        LessonType.RoomSubstitution -> colorResource(CommonR.color.room_substituted_lesson)
        LessonType.Assignment -> colorResource(CommonR.color.assignment_substituted_lesson)
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
            )
        },
        onClick = { },
        secondaryLabel = {
            val fontScale = LocalDensity.current.fontScale;
            val shouldShow = fontScale < 1.19
            if (shouldShow) {
                Text("Mit ${formatter.formatTeacherName(lesson.Teacher)}")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = chipColors,
        border = if (isCurrent) outlinedChipBorder(
            borderWidth = 2.dp,
        ) else chipBorder(),
    )
}

@Composable
fun measureTimeComp(block: @Composable () -> Unit): Int {
    val t0 = Instant.now();
    block.invoke()
    val t1 = Instant.now();
    return ChronoUnit.MILLIS.between(t0, t1).toInt();
}

fun measureTime(block: () -> Unit): Int {
    val t0 = Instant.now();
    block.invoke()
    val t1 = Instant.now();
    return ChronoUnit.MILLIS.between(t0, t1).toInt();
}