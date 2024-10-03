package de.zenonet.stundenplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import java.time.LocalTime
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
    StundenplanTheme(darkTheme = true) {


        Surface {
            Column {

                CurrentLessonInfo(viewModel)

                LaunchedEffect(key1 = null) {
                    viewModel.loadQuoteOfTheDay()
                }
                val quote by viewModel.quoteOfTheDay.collectAsStateWithLifecycle(null)
                if (quote != null && quote!!.text != null)
                    QuoteView(quote!!)


                DailyStaircaseAnalysis(viewModel)
            }

        }

    }

}

@Composable
fun QuoteView(quote: Quote, modifier: Modifier = Modifier) {
    Widget {
        Heading(if (quote.classification == null) "Zitat des Tages" else quote.classification!!)
        Spacer(Modifier.height(10.dp))
        Text(
            quote.text,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("  " + quote.author)
    }

}

@Composable
fun CurrentLessonInfo(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    val dayOfWeek = Timing.getCurrentDayOfWeek()
    // Don't show this on week-ends
    if (dayOfWeek > 4 || dayOfWeek < 0) return


    LaunchedEffect(Unit) {
        vm.startRegularDataRecalculation()
    }

    if (vm.currentPeriod == -1 || vm.currentTime.isBefore(LocalTime.of(8, 0))) return

    val timeTable by vm.currentTimeTable.collectAsStateWithLifecycle(null)
    Widget {
        if (!vm.isBreak)
            Heading("${vm.currentPeriod + 1}. Stunde")
        else
            Heading("Pause vor der ${vm.currentPeriod + 1}. Stunde")

        // Show start- and end time of lesson or break
        Text("Von ${vm.startTime} bis ${vm.endTime} ${if (vm.lessonProgress > 0) " (${vm.lessonProgress}%)" else ""}")
        Spacer(Modifier.height(10.dp))

        if (vm.isBreak && timeTable != null)
            Text("Nach der Pause:", fontWeight = FontWeight.Bold)

        val day: Array<Lesson?>? = timeTable?.Lessons?.get(dayOfWeek)
        val lesson: Lesson? =
            if (day != null && day.size > vm.currentPeriod) day[vm.currentPeriod] else null

        // if lesson is null, this means, it's a regular free period
        if (lesson != null) {
            LessonInfoSentence(lesson)
        } else if (day != null) {

            var nextPeriod = vm.currentPeriod + 1
            while (nextPeriod < day.size && !Lesson.doesTakePlace(day[nextPeriod])) nextPeriod++

            if (nextPeriod < day.size) {
                // The loop can only stop if nextPeriod reached the end (in which case we wouldn't be in this if) or if day[nextPeriod] is not null, so nextLesson will never be null
                val nextLesson: Lesson = day[nextPeriod]!!
                Text("Freistunde")
                Text("Danach:", fontWeight = FontWeight.Bold)
                Text("${nextLesson.Subject} in ${nextLesson.Room} (beginnt um ${nextLesson.StartTime})")

            }
        }
    }
}

@Composable
fun LessonInfoSentence(lesson: Lesson) {
    Text("${lesson.Subject} mit ${lesson.Teacher} ${if (!lesson.isTakingPlace) "(Ausfall)" else ""}")
}

@Composable
fun DailyStaircaseAnalysis(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(null) {
        vm.analyzeStaircaseUsage()
    }

    if (!vm.stairCaseAnalysisCompleted) return

    Widget {
        Heading("Treppensteig-Analyse")
        Spacer(Modifier.height(10.dp))

        if (vm.stairCasesUsedToday > 0)
            Text("Treppen heute: ${vm.stairCasesUsedToday}")
        Text("Treppen diese Woche: ${vm.stairCasesUsedThisWeek}")


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
fun Widget(
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    var show by rememberSaveable { mutableStateOf(true) }
    AnimatedVisibility(show) {
        Box {

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(38, 36, 42),
                ),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
            ) {

                Column(modifier.padding(16.dp)) {
                    content()
                }
            }

            IconButton(
                onClick = {show = false},
                modifier = Modifier
                    .padding(8.dp)
                    .align(TopEnd)
                    .size(36.dp)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                )
            }
        }

    }
}

@Composable
@Preview(device = "spec:width=411dp,height=891dp", showSystemUi = false)
fun Preview(modifier: Modifier = Modifier) {
    val previewQuote = Quote()
    previewQuote.text = "Wie soll ich meine Wunden heilen, wenn ich die Zeit nicht empfinde?"
    previewQuote.author = "Leonard (Memento (2000))"
    Timing.TimeOverride = LocalTime.of(9, 36)
    Main(viewModel = NonCrucialViewModel(null, previewQuote))
}