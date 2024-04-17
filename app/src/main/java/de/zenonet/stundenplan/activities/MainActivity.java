package de.zenonet.stundenplan.activities;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import de.zenonet.stundenplan.R;

public class MainActivity extends AppCompatActivity {

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