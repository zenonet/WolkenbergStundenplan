package de.zenonet.stundenplan.common

import android.util.Log
import de.zenonet.stundenplan.common.timetableManagement.LessonType
import de.zenonet.stundenplan.common.timetableManagement.TimeTable
import java.io.ByteArrayOutputStream
import java.util.HashSet
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
fun serializeTimeTableToBase64(tt: TimeTable): String {
    val stream = ByteArrayOutputStream()


    // write version to stream
    stream.write(0) // zero indicate that this is not the first format which didn't have a version
    stream.write(1) // actual version code

    // Generate lookup table
    val lookupSet = HashSet<String>()

    tt.Lessons.flatten().filterNotNull().forEach{
        lookupSet.add(it.Room)
        lookupSet.add(it.Subject)
        lookupSet.add(it.Teacher)
    }

    val lookupCount = lookupSet.size

    val lookup = lookupSet.toTypedArray()
    stream.write(lookupCount)

    lookup.forEach {
        // TODO: Make sure the deserializer no longer reads 2-byte-chars as 2 bytes when this is pushed to prod
        val nameBytes = it.toByteArray(Charsets.UTF_8)
        stream.write(nameBytes.size)
        stream.write(nameBytes)
    }

    val lookupBytes = stream.size()
    Log.i(LogTags.Debug, "Serialization: Lookup is $lookupBytes bytes long")

    // Serialize outline
    tt.Lessons.forEach {
        stream.write(it.size)
    }

    val bytesBeforeData = stream.size()
    // Serialize actual data
    tt.Lessons.flatten().forEach {
        if(it == null){
            stream.write(0)
            //stream.write(0)
            //stream.write(0)
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
        stream.write(teacherByte)
    }
    Log.i(LogTags.Debug, "Serialization: Data is ${stream.size()-bytesBeforeData} bytes long")
    Log.i(LogTags.Debug, "Serialization: TimeTable is ${stream.size()} bytes long in total")

    return Base64.encode(stream.toByteArray())

}

fun getShareUrlForTimeTable(tt: TimeTable): String =
    "https://zenonet.de/stundenplan/preview?data=${serializeTimeTableToBase64(tt)}"
