package de.zenonet.stundenplan.activities;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.TimePicker;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import de.zenonet.stundenplan.NameLookup;
import de.zenonet.stundenplan.R;
import de.zenonet.stundenplan.TimeTableClient;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    public TimeTableClient client = new TimeTableClient();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        findViewById(R.id.start_view_button).setOnClickListener((args) -> {
            Intent intent = new Intent(MainActivity.this, TimeTableViewActivity.class);
            startActivity(intent);
        });

        /*

        TimeTableClient client = new TimeTableClient();
        client.init(this);*/

/*
        client.loadTimeTableAsync(Calendar.getInstance().get(Calendar.WEEK_OF_YEAR), timeTable -> {
            System.out.println(timeTable);
        });*/
/*
        tokenView = findViewById(R.id.tokenView);
        tokenView.setText(client.token);

        tp = findViewById(R.id.timePicker);
        lookupButton = findViewById(R.id.lookupButton);
*/
    }
}