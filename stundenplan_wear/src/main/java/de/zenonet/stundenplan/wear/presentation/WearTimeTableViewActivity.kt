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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeSource
import androidx.wear.compose.material.TimeText
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.wear.R
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
                    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(
                        "refreshToken",
                        "0.AQUAbReNd3UPKkevzKhVU6sG3anigvv9Ho5KmsaSQTq0tYsbAco.AgABAwEAAADnfolhJpSnRYB1SVj-Hgd8AgDs_wUA9P_Err3T8o2FeTlvxGNOQ4DGOdPqJhIytxx3V4z5C_bCQviq2nidiRjg7GFrwSiapN6cFuZ0A1A-KYXFknkPo0N0DbYnhMqw_R6uV8ki9GK_8cGxhTkafTPu284x9RuqNaDfIWEGyoNdHFia5vfCBaZFLAgVlLucNrLf7W6rWApXKL3Pv-4moLuevOkUfur1Tsly69Dr-RJLT9LRandkn31TTmJh1BlLx5eENhzK8bMtY9XFIu9ZpLN-tn-Jsbk57mX2UTpiTKVSrL9BZfmuigMJ8QVQas4ULyHypEGO6sfasWoS6LZcUpDbnS0Ao4bnj-3yMLNRZzSZ1uvQMrEbvUB7nhSO7Z93FUMvYE6vuOxL7s22Ma238lpiT0xYW6-sSkeIonluo9lAwu2Ae1nd0A_GmbDmM7HhCfoNko4AtePvhL7Ub1zhivBwrKCxclL22Y4Pie63-6GnqjhIQ8FXqKjE4l97cUoTReGYjUBOh1WkQb6FtbdR-7RNy0i3cS1UZe3VLFT0Esrby2pPd6B_HvR9MkaNq8YCQvLxFPEGJI4hCrPhVUJFNRYGqDjh4pT02OCR79Im_K7QOPL3lcqoBXcGN-sqEgx0jA71Zpp6MNZFewdJ0PdRV2IPR2VWhlMm2bZs7oRwbbBRqcjIVKXchd3FGV9yJhHKQn9udm2W5tWj3MF2Px--QdJSdKKZMMyRujjUdNlYaxMfMfVdBx-NklPc8y3P2ZkTi_D71YwasdDvboeq0SeKK_yjrUQv7Azhh-MEpnyN7dY"
                    )
                        .apply();

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

            HorizontalPager(
                state = pagerState,
                Modifier.fillMaxSize(),
            ) { day ->


                ScalingLazyColumn(
                    Modifier
                        //.verticalScroll(rememberScrollState())
                        .padding(20.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    state = rememberScalingLazyListState(),
                ) {
                    if (timeTable == null) return@ScalingLazyColumn;

                    val currentPeriod = Utils.getCurrentPeriod(LocalTime.now().minusHours(4))

                    items(timeTable!!.Lessons[day].size) { period ->
                        LessonView(
                            lesson = timeTable!!.Lessons[day][period],
                            currentPeriod == period,
                            period+1
                        )
                    }

                }

            }
        }
    }
}

@Composable
fun LessonView(lesson: Lesson, isCurrent: Boolean = false, displayPeriod: Int) {
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
    )
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Left,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
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
