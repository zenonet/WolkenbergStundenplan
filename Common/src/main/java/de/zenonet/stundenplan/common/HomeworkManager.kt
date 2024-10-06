package de.zenonet.stundenplan.common

import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import org.json.JSONObject
import java.io.File

object HomeworkManager {
    fun putNoteFor(year:Int, week:Int, dayOfWeek:Int, subjectAbbreviationHash:Int, note:String){
        val (file, root, day) = getJSONObjectForThisDay(year, week, dayOfWeek)
        day.put(subjectAbbreviationHash.toString(), note)
        Utils.writeAllText(file, root.toString())
    }

    fun deleteNoteFor(year:Int, week:Int, dayOfWeek:Int, subjectAbbreviationHash:Int){
        val (file, root, day) = getJSONObjectForThisDay(year, week, dayOfWeek)
        day.remove(subjectAbbreviationHash.toString())
        Utils.writeAllText(file, root.toString())
    }

    fun getNoteFor(year:Int, week:Int, dayOfWeek:Int, subjectAbbreviationHash:Int): String{
        val (_, _, day) = getJSONObjectForThisDay(year, week, dayOfWeek)
        if (!day.has(subjectAbbreviationHash.toString())) return ""

        return day.getString(subjectAbbreviationHash.toString())
    }

    fun populateTimeTable(year: Int, week: Int, tt:TimeTable){
        val (_, root) = loadJsonObject()

        val thisYear = Utils.getOrAppendJSONObject(root, year.toString())
        val thisWeek = Utils.getOrAppendJSONObject(thisYear, week.toString())

        for (day in tt.Lessons.indices) {
            if(!thisWeek.has(day.toString())) continue

            val jsonDay = thisWeek.getJSONObject(day.toString())
            for (lesson in tt.Lessons[day]) {
                if(lesson == null) continue
                val hash = lesson.SubjectShortName.hashCode().toString()
                lesson.HasHomeworkAttached = jsonDay.has(hash) && jsonDay.getString(hash).isNotBlank()
            }
        }

    }

    private fun getJSONObjectForThisDay(year:Int, week:Int, dayOfWeek:Int): Triple<File, JSONObject, JSONObject> {
        // Structure of homework.json: year->week->day->subjectHashCode
        val (file, root) = loadJsonObject()

        val thisYear = Utils.getOrAppendJSONObject(root, year.toString())
        val thisWeek = Utils.getOrAppendJSONObject(thisYear, week.toString())
        val thisDay = Utils.getOrAppendJSONObject(thisWeek, dayOfWeek.toString())
        return Triple(file, root, thisDay)
    }

    private fun loadJsonObject(): Pair<File, JSONObject> {
        val file = File(StundenplanApplication.application.dataDir, "homework.json")

        val root: JSONObject;
        if (file.exists()) {
            root = JSONObject(Utils.readAllText(file))
        } else {
            file.createNewFile()
            Utils.writeAllText(file, "{}")
            root = JSONObject()
        }
        return Pair(file, root)
    }
}