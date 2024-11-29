package de.zenonet.stundenplan.common

import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import java.io.ByteArrayOutputStream
import java.util.HashSet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
fun serializeTimeTableToBase64(tt: TimeTable): String {
    val stream = ByteArrayOutputStream()


    // Generate lookup table
    val lookupSet = HashSet<String>()

    tt.Lessons.flatten().filterNotNull().forEach{
        lookupSet.add(it.Room)
        lookupSet.add(it.Subject)
        lookupSet.add(it.SubjectShortName)
        lookupSet.add(it.Teacher)
    }

    val lookupCount = lookupSet.size

    val lookup = lookupSet.toTypedArray()
    stream.write(lookupCount)

    lookup.forEach {
        stream.write(it.length)
        stream.write(it.toByteArray(Charsets.UTF_8))
    }

    // Serialize outline
    tt.Lessons.forEach {
        stream.write(it.size)
    }

    // Serialize actual data
    tt.Lessons.flatten().forEach {
        if(it == null){
            stream.write(0)
            stream.write(0)
            stream.write(0)
            stream.write(0)
            return@forEach
        }

        val offset = 1

        val type = when(it.Type){
            LessonType.Regular -> 0
            LessonType.Cancelled -> 3
            LessonType.Assignment -> 3
            LessonType.Substitution -> 2
            LessonType.RoomSubstitution -> 1
            LessonType.Absent -> 3
            LessonType.ExtraLesson -> 1
            LessonType.Holiday -> 3
        }

        val roomByte = type.and(1).shl(7).or(lookup.indexOf(it.Room)+offset)
        val teacherByte = type.and(2).shl(6).or(lookup.indexOf(it.Teacher)+offset)

        stream.write(roomByte)
        stream.write(lookup.indexOf(it.Subject)+offset)
        stream.write(lookup.indexOf(it.SubjectShortName)+offset)
        stream.write(teacherByte)
    }

    return Base64.encode(stream.toByteArray())

}

fun getShareUrlForTimeTable(tt: TimeTable): String =
    "https://zenonet.de/stundenplan/preview?data=${serializeTimeTableToBase64(tt)}"