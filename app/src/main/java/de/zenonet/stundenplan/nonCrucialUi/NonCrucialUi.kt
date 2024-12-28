package de.zenonet.stundenplan.nonCrucialUi

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.nonCrucialUi.widgets.Widget
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
            ProvidePreferenceLocals {
                Column {

                    CurrentLessonInfo(viewModel)
                    UpdateNotices(viewModel)

                    LaunchedEffect(key1 = null) {
                        viewModel.loadQuoteOfTheDay()
                    }
                    val quote by viewModel.quoteOfTheDay.collectAsStateWithLifecycle(null)
                    AnimatedVisibility(quote != null && quote!!.text != null) {
                        QuoteView(quote!!)
                    }

                    Homework(viewModel)

                    DailyStaircaseAnalysis(viewModel)
                    //if(viewModel.showReviewRequest) FeedbackPls(viewModel)

                    //Posts(viewModel)

                    WidgetConfigurator()
                }
            }

        }

    }

}

@Composable
fun QuoteView(quote: Quote, modifier: Modifier = Modifier) {
    Widget(NonCrucialWidgetKeys.QUOTE) {
        Heading(if (quote.classification == null) "Zitat des Tages" else quote.classification!!)
        Spacer(Modifier.height(10.dp))
        Text(
            quote.text,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp
        )

        Spacer(Modifier.height(10.dp))
        Text(quote.author, modifier = Modifier.padding(10.dp, 0.dp))
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
    LaunchedEffect(timeTable) {
        // Additionally to the time-based recomputations, recompute when the timetable changes
        vm.generateCurrentLessonInfoData()
    }
    Widget(NonCrucialWidgetKeys.CURRENT_LESSON_INFO) {
        if (!vm.isBreak)
            Heading("${vm.currentPeriod + 1}. Stunde")
        else
            Heading("Pause vor der ${vm.currentPeriod + 1}. Stunde")

        // Show start- and end time of lesson or break
        Text("Von ${vm.startTime} bis ${vm.endTime} ${if (vm.lessonProgress > 0) " (${vm.lessonProgress}%)" else ""}")
        Spacer(Modifier.height(10.dp))

        if (vm.isBreak && timeTable != null && !vm.isFreeSection)
            Text("Nach der Pause:", fontWeight = FontWeight.Bold)

        // if lesson is null, this means, it's a regular free period
        if (vm.currentLesson != null) {
            LessonInfoSentence(vm.currentLesson!!)
        }
        if (vm.isFreeSection) {
            Text("Frei von ${vm.freeSectionStartTime} bis ${vm.freeSectionEndTime} (${vm.freeSectionProgress}%)")
            if (vm.nextActualLesson != null) {
                Text("Danach:", fontWeight = FontWeight.Bold)
                Text("${vm.nextActualLesson!!.Subject} in ${vm.nextActualLesson!!.Room} (beginnt um ${vm.nextActualLesson!!.StartTime})")
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

    AnimatedVisibility(vm.stairCaseAnalysisCompleted) {
        Widget(NonCrucialWidgetKeys.STAIRCASE_ANALYSIS) {
            Heading("Treppensteig-Analyse")
            Spacer(Modifier.height(10.dp))

            if (vm.stairCasesUsedToday > 0)
                Text("Treppen heute: ${vm.stairCasesUsedToday}")
            Text("Treppen diese Woche: ${vm.stairCasesUsedThisWeek}")


        }
    }
}

@Composable
fun UpdateNotices(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.checkForAppUpdates(context)
    }

    AnimatedVisibility(vm.isAppUpdateAvailable) {
        Widget(NonCrucialWidgetKeys.UPDATE_NOTICES) {
            Heading("Ein Update ist verfügbar")
            Spacer(Modifier.height(10.dp))

            val coroutineScope = rememberCoroutineScope()

            Text("Updates werden eventuell benötigt, damit Dein Stundenplan ausgelesen werden kann und können neue Features bringen")
            Row {
                Button({
                    coroutineScope.launch {
                        if (context is Activity)
                            vm.updateAppNow(context)
                    }
                }) { Text("Jetzt updaten") }
                Spacer(Modifier.width(12.dp))
                Button({
                    coroutineScope.launch {
                        vm.dontUpdateAppNow()
                    }
                }) { Text("Nicht jetzt") }
            }

        }
    }
}

val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.")

@Composable
fun Homework(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.loadHomework()
    }
    val showTutorialState = rememberPreferenceState("nonCrucialUi.showHomeworkTutorial", true)

    AnimatedVisibility(vm.homeworkEntries != null && (showTutorialState.value || vm.homeworkEntries!!.isNotEmpty())) {
        Widget(NonCrucialWidgetKeys.HOMEWORK) {
            Heading("Hausaufgaben")
            Spacer(Modifier.height(10.dp))
            if (!(vm.homeworkEntries!!.isEmpty())) {
                for (homeworkEntry in vm.homeworkEntries!!) {
                    val dayOfWeek = Utils.getWordForDayOfWeek(homeworkEntry.day.dayOfWeek.value - 1)

                    Text(
                        buildAnnotatedString {
                            withLink(LinkAnnotation.Clickable("yeah", linkInteractionListener = {
                                vm.openHomeworkEditor(homeworkEntry, context)
                            })) {
                                append(
                                    "${homeworkEntry.lesson.Subject} für ${dayOfWeek.substring(0..1)}. ${
                                        homeworkEntry.day.format(
                                            fmt
                                        )
                                    }"
                                )
                            }
                        },
                        textDecoration = TextDecoration.Underline,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            } else {
                if (showTutorialState.value) {
                    Text("Um Hausaufgaben einzutragen:")
                    Text("- Tippe auf eine Unterrichtsstunde")
                    Text("- Tippe auf 'Hausaufgaben eintragen'")
                    Spacer(Modifier.height(5.dp))
                    Button({
                        showTutorialState.value = false
                    }) {
                        Text("Nicht mehr anzeigen");
                    }
                }
            }

        }
    }
}

@Composable
fun FeedbackPls(vm: NonCrucialViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    Widget(NonCrucialWidgetKeys.FEEDBACK_PLS) {
        Heading("Gefällt Dir diese App?")
        Spacer(Modifier.height(10.dp))

        Text("Könntest du bitte eine Rezension schreiben, um dem Entwickler Feedback zu geben?")
        Row {
            Button({
                coroutineScope.launch {
                    vm.askForPlayStoreReview(context)
                }
            }) {
                Text("Ja")
            }
            Spacer(Modifier.width(12.dp))

            Button({ hideWidget() }) {
                Text("Nein")
            }
        }

    }
}

@Composable
fun Posts(vm: NonCrucialViewModel, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        vm.loadPosts()
    }


    val posts by vm.posts.collectAsStateWithLifecycle()
    if (posts == null) return
    posts!!.forEach {
        Widget(NonCrucialWidgetKeys.POSTS) {
            Column {
                Heading(it.Title)
                Text("Von ${it.Creator}", fontWeight = FontWeight.Light)
                Spacer(Modifier.height(10.dp))

                Text(it.Text)
            }
        }
    }
}

@Composable
fun WidgetConfigurator() {
    var show by rememberSaveable { mutableStateOf(false) }

    Spacer(Modifier.height(36.dp))
    Column(Modifier.fillMaxWidth()) {
        AnimatedVisibility(!show, modifier = Modifier.align(CenterHorizontally)) {
            Button({ show = !show }) {
                Text("Infokarten konfigurieren")
            }
        }

        AnimatedVisibility(show) {
            Widget("configurator", closingOverride = { show = false }) {
                Heading("Infokarten konfigurieren")
                Spacer(Modifier.height(10.dp))
                Column {
                    WidgetConfigToggle(
                        "Infos zur aktuellen Stunde",
                        NonCrucialWidgetKeys.CURRENT_LESSON_INFO
                    )
                    WidgetConfigToggle("Tägliche Zitate", NonCrucialWidgetKeys.QUOTE)
                    WidgetConfigToggle("Treppenanalyse", NonCrucialWidgetKeys.STAIRCASE_ANALYSIS)
                    WidgetConfigToggle("Hausaufgaben", NonCrucialWidgetKeys.HOMEWORK)
                    WidgetConfigToggle(
                        "Update-Benachrichtigung",
                        NonCrucialWidgetKeys.UPDATE_NOTICES
                    )
                    //WidgetConfigToggle("Posts aus dem offiziellen Stundenplan", NonCrucialWidgetKeys.POSTS)
                    //WidgetConfigToggle("Bitte um Feedback", NonCrucialWidgetKeys.FEEDBACK_PLS)
                }
            }

        }
    }
}

@Composable
fun WidgetConfigToggle(title: String, key: String, modifier: Modifier = Modifier) {
    val v = rememberPreferenceState("nonCrucialUi.$key", true)
    SwitchPreference(v, { Text(title) })
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
@Preview(device = "spec:width=411dp,height=891dp", showSystemUi = false)
fun Preview(modifier: Modifier = Modifier) {
    val previewQuote = Quote()
    previewQuote.text = "Wie soll ich meine Wunden heilen, wenn ich die Zeit nicht empfinde?"
    previewQuote.author = "Leonard (Memento (2000))"
    Timing.TimeOverride = LocalTime.of(9, 36)
    Main(viewModel = NonCrucialViewModel(null, previewQuote))
}