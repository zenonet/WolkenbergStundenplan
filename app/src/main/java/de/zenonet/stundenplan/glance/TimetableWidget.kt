package de.zenonet.stundenplan.glance

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.preference.PreferenceManager
import de.zenonet.stundenplan.OnboardingActivity
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.R
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TimetableWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<TimeTable?>
        get() = object : GlanceStateDefinition<TimeTable?> {
            override suspend fun getDataStore(
                context: Context,
                fileKey: String
            ) = TimeTableDataStore(context)


            override fun getLocation(context: Context, fileKey: String) = throw NotImplementedError("Not implemented for TimeTable App Widget State Definition")

        }

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        // In this method, load data needed to render the AppWidget.
        // Use `withContext` to switch to another thread for long running
        // operations.


        // FIXME: Fetch timetable data somewhere composable
        Log.i(LogTags.HomeScreenWidgets, "Got timetable for app widget")

        provideContent {
            // create your AppWidget here
            GlanceTheme {

                val tt = currentState<TimeTable?>()
                Log.i(LogTags.Debug, "Recomposing... ${tt != null}")
                if (tt == null)
                    LoginRequest()
                else
                    MyContent(tt)
            }
        }
    }
}

class TimeTableDataStore(private val context: Context) :
    DataStore<TimeTable?> {
    override val data: Flow<TimeTable?>
        get() = flow {
            val tt: TimeTable? = withContext(Dispatchers.IO) {
                if (PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("showPreview", false)
                ) {
                    Utils.getPreviewTimeTable(context)
                } else {
                    val manager = TimeTableManager()
                    manager.init(context)
                    try {
                        manager.getCurrentTimeTable()
                    } catch (e: TimeTableLoadException) {
                        null
                    }
                }
            }
            emit(tt)
        }


    override suspend fun updateData(transform: suspend (t: TimeTable?) -> TimeTable?): TimeTable? {
        TODO("Not yet implemented")
    }

}

@Composable
private fun MyContent(timeTable: TimeTable) {
    val (sizeX, _) = LocalSize.current
    LazyColumn(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.background).padding(0.dp),
        //verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val dayOfWeek = Timing.getCurrentDayOfWeek()
        itemsIndexed(timeTable.Lessons[dayOfWeek]) { index, lesson ->
            val col = getBackgroundColorForLesson(lesson)
            Row(GlanceModifier.fillMaxWidth().cornerRadius(5.dp).background(col).padding(15.dp)) {
                //Text("${lesson.SubjectShortName} in ${lesson.Room} mit ${lesson.Teacher}")
                Text(
                    "${index + 1}. ${lesson.SubjectShortName}",
                    GlanceModifier.defaultWeight(),
                    style = TextStyle(textAlign = TextAlign.Left),
                )
                if (sizeX > 100.dp) {
                    androidx.glance.layout.Spacer(GlanceModifier.defaultWeight())

                    Text(lesson.Room, style = TextStyle(textAlign = TextAlign.Right))
                }
            }
        }

        /*Text(text = "Where to?", modifier = GlanceModifier.padding(12.dp))
        Row(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                text = "Home",
                onClick = actionStartActivity<TimeTableViewActivity>()
            )
        }*/
    }
}

@Composable
fun LoginRequest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().background(colorResource(R.color.error_color)).padding(15.dp)
            .clickable {
                val intent = Intent(context, OnboardingActivity::class.java)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }) {
        Text("Logge dich ein, um deinen Stundenplan hier zu sehen")

    }
}

@Composable
fun getBackgroundColorForLesson(lesson: Lesson): Color {
    if (!lesson.isTakingPlace/* && lesson.Type != LessonType.Assignment*/) return colorResource(R.color.cancelled_lesson)

    return when (lesson.Type) {
        LessonType.Substitution -> colorResource(R.color.substituted_lesson)
        LessonType.RoomSubstitution -> colorResource(R.color.room_substituted_lesson)
        LessonType.Assignment -> colorResource(R.color.assignment_substituted_lesson)
        else -> {
            colorResource(R.color.regular_lesson)
        }
    }
}

@Composable
fun colorResource(id: Int): Color {
    val context = LocalContext.current
    return Color(context.resources.getColor(id, context.theme))
}

suspend fun loadTimeTable(context: Context): TimeTable? {
    val tt: TimeTable? = withContext(Dispatchers.IO) {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("showPreview", false)
        ) {
            Utils.getPreviewTimeTable(context)
        } else {
            val manager = TimeTableManager()
            manager.init(context)
            try {
                manager.getCurrentTimeTable()
            } catch (e: TimeTableLoadException) {
                null
            }
        }
    }
    return tt
}

fun updateWidgets(context: Context){
    suspend {
        TimetableWidget().updateAll(context)
    }
}

fun areWidgetsExistent(context: Context) =
    runBlocking {
        GlanceAppWidgetManager(context).getGlanceIds(TimetableWidget::class.java)
    }.isNotEmpty()