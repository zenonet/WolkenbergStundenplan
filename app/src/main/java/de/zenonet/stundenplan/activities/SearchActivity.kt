package de.zenonet.stundenplan.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastSumBy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.NameLookup
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.homework.ViewModelFactory
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class SearchActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val lookup = StundenplanApplication.getAuxiliaryManager().lookup

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
    val focusRequester =  FocusRequester()
    val context = LocalContext.current

    val listState = remember { LazyListState() }

    val coroutineScope = rememberCoroutineScope()

    fun scrollToItem(){
        coroutineScope.launch{
            listState.animateScrollToItem(vm.selectedCompletion, -150)
        }
    }

    LaunchedEffect(vm.selectedCompletion == 0) {
        if(vm.selectedCompletion == 0) scrollToItem()
    }

    TextField(
        value = vm.searchFieldText,
        onValueChange = {
            vm.searchFieldText = it
            vm.generateCompletions()
        },
        placeholder = {
            Text("Suchen")
        },
        singleLine = true,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                Log.d(LogTags.Debug, "Focus changed to $it")
            }
            .fillMaxWidth()
            .onPreviewKeyEvent {
                if(it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if(it.key == Key.Escape){
                    if(context is Activity){
                        context.finish()
                    }
                    return@onPreviewKeyEvent true
                }else if(vm.completions.isNotEmpty()){
                    if(it.key == Key.Enter && vm.completions.size > vm.selectedCompletion) {
                        vm.completionClicked(context, vm.completions[vm.selectedCompletion])
                        return@onPreviewKeyEvent true
                    }
                    else if(it.key == Key.DirectionUp) {
                        if(vm.selectedCompletion > 0){
                            vm.selectedCompletion -= 1
                            scrollToItem()
                        }
                        return@onPreviewKeyEvent true
                    }
                    else if(it.key == Key.DirectionDown) {
                        if(vm.selectedCompletion < vm.completions.size-1){
                            vm.selectedCompletion += 1
                            scrollToItem()
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .onKeyEvent {
                it.key == Key.DirectionUp || it.key == Key.DirectionDown
            }
    )

    LaunchedEffect(Unit) {

        focusRequester.requestFocus()
        if(focusRequester.captureFocus()){
            Log.i(LogTags.Debug, "Successfully captured focus for search field")
        }else{
            Log.e(LogTags.Debug, "Failed to capture focus for search field")
        }
    }

    LazyColumn(
        Modifier
            .clip(RoundedCornerShape(0.dp, 0.dp, 10.dp, 10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(0.dp, 0.dp, 10.dp, 10.dp)
            ),
        state = listState
    ) {
        items(vm.completions.size) { i ->
            val completion = vm.completions[i]
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Completion(completion, vm, vm.selectedCompletion == i)
        }
    }
}

@Composable
fun Completion(completion: Student, vm: SearchViewModel, isSelected: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val mod = if(isSelected){
        modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
    }else{
        modifier
    }

    Column(
        mod
            .fillMaxWidth()
            .clickable {
                vm.completionClicked(context, completion)
            }
            .padding(10.dp)
        ) {
        Text(completion.name, fontSize = 16.sp)
        Text("Schüler • " + completion.cClass/* + " • " + completion.id*/, fontSize = 12.sp)
    }

}

class SearchViewModel(private val lookup: NameLookup) : ViewModel() {
    var searchFieldText by mutableStateOf("");
    var completions by mutableStateOf<List<Student>>(listOf());
    var selectedCompletion by mutableIntStateOf(0)
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
        viewModelScope.launch(viewModelScope.coroutineContext){
            val start = Instant.now()
            val searchText = searchFieldText

            withContext(Dispatchers.Default) {
                val searchTextParts = searchText.split(' ')

                completions = students.fastMap { st ->
                    // Parts that start with a search term, count extra
                    val searchScore = searchTextParts.fastSumBy{

                        if (it.isNotEmpty() && it[0].isDigit()) {
                            // Search by ids
                            if(st.id == it.toIntOrNull()){
                                return@fastSumBy 1
                            }
                            // Search by classes
                            if (st.cClass.trimStart('0').startsWith(it, ignoreCase = true)) {
                                return@fastSumBy 1
                            }
                        }

                        if(st.searchName.contains(it, ignoreCase = true)){
                            if(st.searchName.startsWith(it, ignoreCase = true)){
                                if(searchTextParts[0] == it) return@fastSumBy 3
                                return@fastSumBy 2
                            }
                            return@fastSumBy 1
                        }
                        0
                    }

                    Pair<Student, Int>(st, searchScore)
                }.filter { it.second > 0 }.sortedByDescending { it.second }.fastMap { it.first }
            }

            selectedCompletion = 0

            Log.i(
                LogTags.Timing,
                "Generating completions took ${
                    java.time.Duration.between(start, Instant.now()).toMillis()
                }ms"
            )
        }
    }

    fun completionClicked(context: Context, student: Student) {
        val intent = Intent(context, OthersTimeTableViewActivity::class.java)
        intent.putExtra("studentId", student.id)
        context.startActivity(intent)
    }
}

data class Student(
    val id: Int,
    val name: String,
    val searchName: String = name.replace('-', ' '),
    val cClass: String,
)