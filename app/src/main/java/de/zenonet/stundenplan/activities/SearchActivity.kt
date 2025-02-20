package de.zenonet.stundenplan.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.NameLookup
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.homework.ViewModelFactory
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import java.time.Instant

class SearchActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // TODO: Remove dependency on TimeTableManager
        val manager = TimeTableManager()
        manager.init(this)

        val lookup = manager.lookup

        val viewModelFactory = ViewModelFactory {
            SearchViewModel(lookup)
        }
        setContent {
            val vm = viewModel<SearchViewModel>(factory = viewModelFactory)
            LaunchedEffect(Unit) {
                vm.generateCompletions()
            }
            StundenplanTheme {
                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    TopAppBar(
                        title = {
                            Text("Suche")
                        },
                        navigationIcon = {
                            IconButton({
                                finishAndRemoveTask()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }) { innerPadding ->
                    Column(
                        Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(25.dp)
                    ) {
                        Search(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun Search(vm: SearchViewModel) {
    TextField(
        value = vm.searchFieldText,
        onValueChange = {
            vm.searchFieldText = it
            vm.generateCompletions()
        },
        placeholder = {
            Text("Suchen")
        },
        modifier = Modifier.fillMaxWidth()
    )
    LazyColumn(
        Modifier
            .clip(RoundedCornerShape(0.dp, 0.dp, 10.dp, 10.dp))
            .border(
                1.dp,
                Color(61, 61, 61, 255),
                shape = RoundedCornerShape(0.dp, 0.dp, 10.dp, 10.dp)
            )
    ) {
        items(vm.completions.size) { i ->
            val completion = vm.completions[i]
            HorizontalDivider(color = Color(61, 61, 61, 255))
            Completion(completion, vm)
        }
    }
}

@Composable
fun Completion(completion: Student, vm: SearchViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .clickable {
                vm.completionClicked(context, completion)
            }
            .padding(10.dp)) {
        Text(completion.name, fontSize = 16.sp)
        Text("Schüler • " + completion.cClass + " • " + completion.id, fontSize = 12.sp)
    }
}

class SearchViewModel(private val lookup: NameLookup) : ViewModel() {
    var searchFieldText by mutableStateOf("");
    var completions by mutableStateOf<List<Student>>(listOf());
    lateinit var students: List<Student>

    init {
        generateStudentList()
    }

    fun generateStudentList() {
        val studentData = lookup.LookupData.getJSONObject("Student")
        students = studentData.keys().asSequence().map {
            val obj = studentData.getJSONObject(it)
            Student(
                id = it.toInt(),
                name = "${obj.getString("FIRSTNAME")} ${obj.getString("LASTNAME")}",
                cClass = lookup.lookupClassName(obj.getInt("CLASS_ID"))
            )
        }.toList()
    }

    fun generateCompletions() {
        val start = Instant.now()
        val searchText = searchFieldText

        val intOrNull = searchText.toIntOrNull()
        if (intOrNull != null) {
            completions = students.filter { it.id.toString().startsWith(searchFieldText) }
        } else {
            completions = students.filter { st ->
                st.name.split(' ').fastAny {
                    it.startsWith(searchText, true)
                }
            }
        }

        Log.i(
            LogTags.Timing,
            "Generating completions took ${
                java.time.Duration.between(start, Instant.now()).toMillis()
            }ms"
        )
    }

    fun completionClicked(context: Context, student: Student) {
        val intent = Intent(context, OthersTimeTableViewActivity::class.java)
        // TODO: Add flags to not reopen activity
        intent.putExtra("studentId", student.id)
        context.startActivity(intent)
    }
}

data class Student(
    val id: Int,
    val name: String,
    val cClass: String,
);