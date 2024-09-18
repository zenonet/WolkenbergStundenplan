package de.zenonet.stundenplan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.zenonet.stundenplan.common.quoteOfTheDay.Quote
import de.zenonet.stundenplan.ui.theme.StundenplanTheme

fun applyUiToComposeView(view: ComposeView, viewModel: NonCrucialViewModel) {
    view.apply {
        setContent {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            Main(viewModel)
        }
    }
}

@Composable
fun Main(viewModel: NonCrucialViewModel, modifier: Modifier = Modifier) {
    StundenplanTheme{
        Surface {
            LaunchedEffect(key1 = null) {
                viewModel.loadQuoteOfTheDay()
            }

            //val state by viewModel.quoteOfTheDay.collectAsStateWithLifecycle(viewModel.quoteOfTheDayDirect)
            val quote by viewModel.quoteOfTheDay.collectAsStateWithLifecycle(null)

            if (quote == null || quote!!.text == null)
                Text("Loading...")
            else
                QuoteView(quote!!)
        }
    }
}

@Composable
fun QuoteView(quote: Quote, modifier: Modifier = Modifier) {
    Box(modifier.padding(15.dp)) {
        Column {
            Text(
                text = (if (quote!!.classification == null) "Zitat des Tages" else quote!!.classification!!),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                quote.text,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(10.dp))
            Text("  " + quote!!.author)
        }
    }
}

@Composable
@Preview(device = Devices.PHONE, showSystemUi = true)
fun Preview(modifier: Modifier = Modifier) {
    val previewQuote = Quote()
    previewQuote.text = "Wie soll ich meine Wunden heilen, wenn ich die Zeit nicht empfinde?"
    previewQuote.author = "Leonard (Memento (2000))"
    Main(viewModel = NonCrucialViewModel(previewQuote))
}