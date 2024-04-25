package de.zenonet.stundenplan;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.zenonet.stundenplan.common.quoteOfTheDay.QuoteProvider;

public class NonCrucialUiFragment extends Fragment {


    public NonCrucialUiFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Start loading quote of the day
        new QuoteProvider().getQuoteOfTheDayAsync(quote -> getActivity().runOnUiThread(() -> {
            ((TextView)getActivity().findViewById(R.id.quoteAuthor)).setText(quote.author);
            ((TextView)getActivity().findViewById(R.id.quoteText)).setText(quote.text);
        }));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_non_crucial_ui, container, false);
    }
}