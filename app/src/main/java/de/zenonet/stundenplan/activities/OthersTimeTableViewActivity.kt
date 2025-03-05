package de.zenonet.stundenplan.activities

import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.zenonet.stundenplan.R
import de.zenonet.stundenplan.TimeTableViewUtils
import de.zenonet.stundenplan.common.Formatter
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.ResultType
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.common.Timing
import de.zenonet.stundenplan.common.Week
import de.zenonet.stundenplan.common.timetableManagement.TimeTableLoadException
import java.time.Duration
import java.time.Instant

class OthersTimeTableViewActivity : AppCompatActivity() {

    lateinit var tableLayout:ViewGroup
    var studentId: Int = -1
    var week: Week = Timing.getRelevantWeekOfYear()
    val timeTableManager = StundenplanApplication.getAuxiliaryManager()
    lateinit var formatter:Formatter;

    val start = Instant.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_others_time_table_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        studentId = intent.getIntExtra("studentId", 666)
        timeTableManager.init(this)
        formatter = Formatter(this)
        findViewById<TextView>(R.id.nameView).text = timeTableManager.lookup.lookupStudent(studentId)
        findViewById<ImageButton>(R.id.previousWeekButton).setOnClickListener{
            week = week.preceedingWeek
            loadTimeTable()
        }

        findViewById<ImageButton>(R.id.nextWeekButton).setOnClickListener{
            week = week.succeedingWeek
            loadTimeTable()
        }
        findViewById<ImageButton>(R.id.currentWeekButton).setOnClickListener {
            week = Timing.getRelevantWeekOfYear()
            loadTimeTable()
        }

        tableLayout = findViewById<ViewGroup>(R.id.tableLayout)

        // Generate table layout
        TimeTableViewUtils.createTableLayout(this,  tableLayout, null)

        loadTimeTable()
    }

    var data: Pair<String?, String?>? = null

    fun loadTimeTable(){
        Thread {
            Log.i(LogTags.Timing, "Activity start until load thread start took ${Duration.between(start, Instant.now()).nano/1000000f}ms")
            val res = timeTableManager.login()

            if(res != ResultType.Success){
                runOnUiThread{
                    Toast.makeText(this, "Login gescheitert", Toast.LENGTH_SHORT).show()
                }
                return@Thread
            }

            timeTableManager.apiClient.loginIfTokenHasExpired()

            // No need to re-fetch if raw data is already loaded
            if(data == null) data = timeTableManager.apiClient.fetchRawDataFromApi("student", studentId)

            Log.i(LogTags.Timing, "Activity start until data fetched start took ${Duration.between(start, Instant.now()).nano/1000000f}ms")
            try {

                val timeTable = timeTableManager.parser.parseWeek(
                    data!!.first,
                    data!!.second,
                    week
                )
                Log.i(LogTags.Timing, "Activity start until data parsed took ${Duration.between(start, Instant.now()).nano/1000000f}ms")

                runOnUiThread {
                    TimeTableViewUtils.updateTimeTableView(this, timeTable, formatter)
                    TimeTableViewUtils.updateDayDisplayForWeek(this, week)
                    Log.i(LogTags.Timing, "Activity start until others timetable display took ${Duration.between(start, Instant.now()).nano/1000000f}ms")
                }
            }catch (e: TimeTableLoadException){
                runOnUiThread{
                    Toast.makeText(this, "Stundenplan konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
                    TimeTableViewUtils.updateTimeTableView(this, null, formatter)
                    TimeTableViewUtils.updateDayDisplayForWeek(this, week)
                }
                return@Thread
            }
        }.start()
    }


    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if(keyCode == KeyEvent.KEYCODE_ESCAPE){
            finish();
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}