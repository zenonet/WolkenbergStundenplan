package de.zenonet.stundenplan.homework

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.zenonet.stundenplan.common.HomeworkManager
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.Week
import de.zenonet.stundenplan.common.timetableManagement.Lesson
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Calendar

class HomeworkEditorViewModel(
    val week: Week,
    val dayOfWeek: Int,
    val subjectAbbreviationHash: Int,
    private val ttm: TimeTableManager?,
    private val previewTimeTable: TimeTable? = null
) : ViewModel() {


    private val _timeTable = MutableStateFlow<TimeTable?>(null)
    val timeTable: StateFlow<TimeTable?> = _timeTable.asStateFlow()
    val lesson: Flow<Lesson?> = timeTable.map {
        if (it != null && period != -1 && it.hasDataForLesson(dayOfWeek, period))
            it.Lessons[dayOfWeek][period]
        else null
    }

    var period by mutableIntStateOf(-1)
    var text by mutableStateOf("")
    var isTextLoaded by mutableStateOf(false)

    suspend fun loadTimeTable() {
        if(timeTable.value != null) return
        if (ttm != null) {
            val tt = withContext(Dispatchers.IO) {
                ttm.login()
                ttm.getTimeTableForWeek(week)
            }
            _timeTable.value = tt
        } else if (previewTimeTable != null) {
            _timeTable.value = previewTimeTable
        }

        // Calculate period
        val lessons = _timeTable.value!!.Lessons[dayOfWeek]
        for(i in lessons.indices){
            if(lessons[i] != null && lessons[i].SubjectShortName.hashCode() == subjectAbbreviationHash){
                period = i
                break
            }
        }
    }

    suspend fun loadExistingText(){
        if(isTextLoaded) return
        withContext(Dispatchers.IO) {
            text = HomeworkManager.getNoteFor(week, dayOfWeek, subjectAbbreviationHash)
            isTextLoaded = true
        }
    }

    suspend fun save() {
        withContext(Dispatchers.IO) {
            HomeworkManager.putNoteFor(week, dayOfWeek, subjectAbbreviationHash, text)
        }
    }

    private fun getJSONObjectForThisDay(): Triple<File, JSONObject, JSONObject> {
        // Structure of homework.json: year->week->day->subjectHashCode
        val file = File(StundenplanApplication.application.dataDir, "homework.json")

        val root: JSONObject;
        if (file.exists()) {
            root = JSONObject(Utils.readAllText(file))
        } else {
            file.createNewFile()
            Utils.writeAllText(file, "{}")
            root = JSONObject()
        }

        val year = Calendar.getInstance().get(Calendar.YEAR).toString()

        val thisYear = Utils.getOrAppendJSONObject(root, year)
        val thisWeek = Utils.getOrAppendJSONObject(thisYear, week.toString())
        val thisDay = Utils.getOrAppendJSONObject(thisWeek, dayOfWeek.toString())
        return Triple(file, root, thisDay)
    }
}

class ViewModelFactory(private val factory:() -> ViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
}