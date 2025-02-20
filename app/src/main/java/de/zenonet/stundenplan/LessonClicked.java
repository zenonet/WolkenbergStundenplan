package de.zenonet.stundenplan;

import android.view.View;

public interface LessonClicked {
    void onLessonClicked(int day, int period, View view);
}
