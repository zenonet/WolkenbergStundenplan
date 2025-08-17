# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class de.zenonet.stundenplan.common.models.User {*;}
-keep class de.zenonet.stundenplan.common.models.UserType {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.Lesson {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTable {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.LessonType {*;}
-keep class de.zenonet.stundenplan.common.quoteOfTheDay.Quote {*;}

-keep class de.zenonet.stundenplan.common.Base64SerializationKt {*;}
-keep class de.zenonet.stundenplan.common.DataNotAvailableException {*;}
-keep class de.zenonet.stundenplan.common.Formatter {*;}
-keep class de.zenonet.stundenplan.common.HomeworkManager {*;}
-keep class de.zenonet.stundenplan.common.NameLookup {*;}
-keep class de.zenonet.stundenplan.common.ResultType {*;}
-keep class de.zenonet.stundenplan.common.StatisticsManager {*;}
-keep class de.zenonet.stundenplan.common.StundenplanApplication {*;}
-keep class de.zenonet.stundenplan.common.Timing {*;}
-keep class de.zenonet.stundenplan.common.Utils {*;}
-keep class de.zenonet.stundenplan.common.Week {*;}
-keep class de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback {*;}
-keep class de.zenonet.stundenplan.common.callbacks.TimeTableLoadFailedCallback {*;}
-keep class de.zenonet.stundenplan.common.callbacks.TimeTableLoadedCallback {*;}
-keep class de.zenonet.stundenplan.common.quoteOfTheDay.QuoteProvider {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTableApiClient {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTableCacheClient {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTableManager {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.TimeTableParser {*;}
-keep class de.zenonet.stundenplan.common.timetableManagement.UserLoadException {*;}
-keep class de.zenonet.stundenplan.common.ReportableError {*;}