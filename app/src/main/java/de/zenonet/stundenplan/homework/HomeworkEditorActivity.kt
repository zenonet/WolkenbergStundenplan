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
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.nonCrucialUi.Heading
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.launch

class HomeworkEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.extras == null) finish()

        val week = intent.extras?.getInt("week")
        val dayOfWeek = intent.extras?.getInt("dayOfWeek")
        val period = intent.extras?.getInt("period")

        if (week == null || dayOfWeek == null || period == null) finish()

        val ttm = TimeTableManager()
        ttm.init(this)

        val vm = HomeworkEditorViewModel(week!!, dayOfWeek!!, period!!, ttm)

        enableEdgeToEdge()
        setContent {
            StundenplanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LaunchedEffect(Unit) {
                        vm.loadTimeTable()
                        vm.loadExistingText()
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
            if (lesson == null) return@Box

            Heading("Hausaufgaben für ${lesson!!.Subject} am ${Utils.getWordForDayOfWeek(vm.dayOfWeek)}")

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
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                placeholder = { Text("Gib hier deine Hausaufgaben für ${lesson!!.Subject} ein") }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeworkEditorPreview() {
    StundenplanTheme {
        val vm =
            HomeworkEditorViewModel(42, 3, 2, null, Utils.getPreviewTimeTable(LocalContext.current))
        Editor(vm)
    }
}