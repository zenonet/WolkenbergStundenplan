package de.zenonet.stundenplan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.common.quoteOfTheDay.QuoteProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NonCrucialViewModel(private val quote: Quote? = null) : ViewModel() {


    private val _quoteOfTheDay = MutableStateFlow<Quote?>(null)
    val quoteOfTheDay: StateFlow<Quote?> = _quoteOfTheDay.asStateFlow()

    private val quoteProvider: QuoteProvider = QuoteProvider()

    init {
        if (quote != null) {
            _quoteOfTheDay.value = quote
        }
    }

    fun loadQuoteOfTheDay() {
        if (quote != null) return

        viewModelScope.launch {
            val q = withContext(Dispatchers.IO) {
                quoteProvider.getQuoteOfTheDay()
            }
            withContext(Dispatchers.Main){
                _quoteOfTheDay.value = q
                Log.i(Utils.LOG_TAG, "Assigned quote to state")
            }
        }

/*
        quoteProvider.getQuoteOfTheDayAsync {
            quoteOfTheDay = it
        }*/

    }
}