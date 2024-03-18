package de.zenonet.stundenplan.activities;

import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.core.view.ViewCompat;
import de.zenonet.stundenplan.LessonType;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.TimeTable;
import de.zenonet.stundenplan.TimeTableClient;

import java.util.Calendar;

public class TimeTableViewActivity extends AppCompatActivity {

    TimeTableClient client;
    LinearLayout table;
    TextView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // We do that before the other stuff to make sure it's already loading asynchronously while the activity boots up

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_table_view);

        client = new TimeTableClient();
        client.init(this);
        client.loadTimeTableAsync(Calendar.getInstance().get(Calendar.WEEK_OF_YEAR), timeTable -> {
            runOnUiThread(() -> updateTimeTableView(timeTable));
        });

        table = findViewById(R.id.tableLayout);
        stateView = findViewById(R.id.stateView);
        createTableLayout();
    }

    private void updateTimeTableView(TimeTable timeTable) {

        stateView.setText(timeTable.isFromCache ? (timeTable.isCacheStateConfirmed ? "From cache (confirmed)" : "From cache") : "From API");
        for (int dayI = 0; dayI < timeTable.Lessons.length; dayI++) {
            for (int periodI = 0; periodI < timeTable.Lessons[dayI].length; periodI++) {
                int viewId = 666 + dayI * 9 + periodI;
                TextView lessonView = findViewById(viewId);

                if (lessonView == null) continue; // TODO: This should never happen

                lessonView.setText(timeTable.Lessons[dayI][periodI].SubjectShortName);

                // TODO: Select better colors for this
                if(!timeTable.Lessons[dayI][periodI].isTakingPlace())
                    lessonView.setBackgroundColor(Color.GREEN);
                else if(timeTable.Lessons[dayI][periodI].Type == LessonType.Substitution)
                    lessonView.setBackgroundColor(Color.RED);
                else
                    lessonView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void createTableLayout() {
        final int widthPerRow = table.getMeasuredWidth() / 5;

        TableRow.LayoutParams rowParams = new TableRow.LayoutParams(50, 500);
        for (int dayI = 0; dayI < 5; dayI++) {
            TableRow row = new TableRow(this);
            //row.setLayoutParams(rowParams);
            //row.setBackgroundColor(dayI % 2 == 0 ? Color.RED : Color.BLUE);
            row.setId(ViewCompat.generateViewId());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setMinimumWidth(widthPerRow);

            LinearLayout innerLayout = new LinearLayout(this);
            innerLayout.setOrientation(LinearLayout.VERTICAL);
            for (int periodI = 0; periodI < 9; periodI++) {
                TextView lessonView = new TextView(this);
                final int lessonTextPaddingH = 30;
                final int lessonTextPaddingV = 15;
                lessonView.setMinWidth(widthPerRow);
                lessonView.setPadding(lessonTextPaddingH, lessonTextPaddingV, lessonTextPaddingH, lessonTextPaddingV);
                lessonView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                lessonView.setId(666 + dayI * 9 + periodI);
                lessonView.setBackground(getDrawable(R.drawable.border));
                lessonView.setTextSize(22);

                innerLayout.addView(lessonView);
            }

            row.addView(innerLayout);
            table.addView(row);
        }
    }
}