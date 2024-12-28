package de.zenonet.stundenplan.homework

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.Week
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.nonCrucialUi.Heading
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.launch

class HomeworkEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras == null) finish()

        val year = intent.extras?.getInt("year")
        val weekOfYear = intent.extras?.getInt("week")
        val dayOfWeek = intent.extras?.getInt("dayOfWeek")
        val subjectHashCode = intent.extras?.getInt("subjectAbbreviationHash")

        if (weekOfYear == null || dayOfWeek == null || subjectHashCode == null || year == null) finish()

        val week = Week(year!!, weekOfYear!!);

        val ttm =
            if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("showPreview", false)) null
            else TimeTableManager();
        ttm?.init(this)

        val viewModelFactory: () -> HomeworkEditorViewModel = {
            HomeworkEditorViewModel(week, dayOfWeek!!, subjectHashCode!!, ttm, Utils.getPreviewTimeTable(this))
        }

        enableEdgeToEdge()
        setContent {
            StundenplanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val vm = viewModel<HomeworkEditorViewModel>(factory = ViewModelFactory(viewModelFactory))
                    LaunchedEffect(Unit) {
                        vm.loadExistingText()
                    }
                    LaunchedEffect(Unit) {
                        vm.loadTimeTable()
                    }

                    Editor(
                        vm,
                        modifier = Modifier.padding(innerPadding).imePadding()
                    )
                }
            }
        }
    }
}

@Composable
fun Editor(vm: HomeworkEditorViewModel, modifier: Modifier = Modifier) {
    Box(modifier.padding(24.dp, 24.dp, 24.dp, 8.dp)) {
        Column {
            val lesson by vm.lesson.collectAsStateWithLifecycle(null)

            Heading("Hausaufgaben ${if(lesson == null) "" else "für ${lesson!!.Subject}"} am ${Utils.getWordForDayOfWeek(vm.dayOfWeek)}")
            Spacer(Modifier.height(10.dp))

            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            Row {
                Button({
                    coroutineScope.launch {
                        vm.save()
                        if(context is Activity)
                            context.finish()

                    }
                }) { Text("Fertig!") }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = vm.text,
                onValueChange = { vm.text = it },
                singleLine = false,
                enabled = vm.isTextLoaded,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                placeholder = { Text("Gib hier deine Hausaufgaben ${if(lesson == null) "" else "für ${lesson!!.Subject}"} ein") }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeworkEditorPreview() {
    StundenplanTheme {
        val vm =
            HomeworkEditorViewModel(Week(2024, 42), 3, 2, null, Utils.getPreviewTimeTable(LocalContext.current))
        Editor(vm)
    }
}