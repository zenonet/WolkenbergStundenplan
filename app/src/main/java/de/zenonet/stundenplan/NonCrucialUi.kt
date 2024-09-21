package de.zenonet.stundenplan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlin.math.roundToInt

fun applyUiToComposeView(view: ComposeView, viewModel: NonCrucialViewModel) {
    view.apply {
        setContent {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            Main(viewModel)
        }
    }
}

@Composable
fun Main(viewModel: NonCrucialViewModel, modifier: Modifier = Modifier) {
    StundenplanTheme {


        Surface {
            Column {
                LaunchedEffect(key1 = null) {
                    viewModel.loadQuoteOfTheDay()
                }

                val quote by viewModel.quoteOfTheDay.collectAsStateWithLifecycle(null)

                if (quote != null && quote!!.text != null)
                    QuoteView(quote!!)

                CurrentLessonInfo(viewModel)

                DailyStaircaseAnalysis(viewModel)
            }

        }

    }

}

@Composable
fun QuoteView(quote: Quote, modifier: Modifier = Modifier) {
    Box(modifier.padding(15.dp)) {
        Column {
            Heading(if (quote!!.classification == null) "Zitat des Tages" else quote!!.classification!!)
            Spacer(Modifier.height(10.dp))
            Text(
                quote.text,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("  " + quote!!.author)
        }
    }
}

@Composable
fun CurrentLessonInfo(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    val day = Timing.getCurrentDayOfWeek()
    // Don't show this on week-ends
    if(day > 4) return

    val currentTime = Timing.getCurrentTime()

    val period = remember { Utils.getCurrentPeriod(currentTime) }
    if (period == -1) return

    val pair = remember { Utils.getStartAndEndTimeOfPeriod(period) }


    val lessonStart = pair.first
    val totalLessonSeconds = pair.second.toSecondOfDay() - lessonStart.toSecondOfDay()
    val progressInSeconds =
        currentTime.toSecondOfDay().toLong() - lessonStart.toSecondOfDay().toLong()
    val progress = (progressInSeconds.toFloat() / totalLessonSeconds * 100).roundToInt()

    val timeTable by vm.currentTimeTable.collectAsStateWithLifecycle(null)
    Box(modifier.padding(15.dp)) {
        Column {

            Heading("Aktuelle Stunde: ${period + 1}.")
            Spacer(Modifier.height(10.dp))

            val day: Array<Lesson>? =
                if (timeTable != null) timeTable!!.Lessons[day] else null

            if (day != null) {
                val lesson = day[period]
                if (lesson != null) {
                    Text("${lesson.Subject} mit ${lesson.Teacher} ${if (!lesson.isTakingPlace) "(Ausfall)" else ""}")
                }
            }
            Text("Von ${pair.first} bis ${pair.second} ${if (progress > 0) " ($progress%)" else ""}")
            if (day != null) {
                val lesson = day[period]

                var nextPeriod = period + 1
                while (nextPeriod < day.size && (day[nextPeriod] == null || !day[nextPeriod].isTakingPlace)) nextPeriod++

                if (nextPeriod < day.size) {

                    val nextLesson = day[nextPeriod]
                    if (lesson == null || !lesson.isTakingPlace) {
                        Text("Freistunde", fontWeight = FontWeight.Bold)
                        Text("Nächste Stunde (${nextLesson.Subject} in ${nextLesson.Room}) beginnt um ${nextLesson.StartTime}")
                    }
                }
            }

        }

    }
}

@Composable
fun DailyStaircaseAnalysis(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    vm.analyzeStaircaseUsage()
    val timeTable by vm.currentTimeTable.collectAsStateWithLifecycle()

    if (timeTable == null) return

    Box(modifier.padding(15.dp)) {
        Column {
            Heading("Treppensteig-Analyse")
            Spacer(Modifier.height(10.dp))

            if(vm.stairCasesUsedToday > 0)
                Text("Treppen heute: ${vm.stairCasesUsedToday}")
            Text("Treppen diese Woche: ${vm.stairCasesUsedThisWeek}")
        }
    }
}

@Composable
fun Heading(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
@Preview(device = Devices.PHONE, showSystemUi = true)
fun Preview(modifier: Modifier = Modifier) {
    val previewQuote = Quote()
    previewQuote.text = "Wie soll ich meine Wunden heilen, wenn ich die Zeit nicht empfinde?"
    previewQuote.author = "Leonard (Memento (2000))"
    Main(viewModel = NonCrucialViewModel(null, previewQuote))
}