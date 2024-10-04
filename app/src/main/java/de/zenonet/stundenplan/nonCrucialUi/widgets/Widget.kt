package de.zenonet.stundenplan.nonCrucialUi.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.zenonet.stundenplan.nonCrucialUi.Heading
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
fun Widget(
    widgetKey: String,
    modifier: Modifier = Modifier,
    closingOverride: (() -> Unit)? = null,
    content: @Composable (WidgetScope.() -> Unit)
) {
    var showConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    val showState = rememberPreferenceState("nonCrucialUi.$widgetKey", true)
    var show by showState
    AnimatedVisibility(show) {
        Box {


            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(38, 36, 42),
                ),
                //elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(5.dp)
            ) {

                Column(modifier.padding(16.dp)) {
                    content(WidgetScope(showState))
                }
            }

            IconButton(
                onClick = {
                    if (closingOverride != null) {
                        closingOverride()
                        return@IconButton
                    }

                    showConfirmationDialog = true
                },
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                )
            }

            if (showConfirmationDialog)
                AlertDialog(
                    title = {
                        Text("Entfernen bestätigen")
                    },
                    text = {
                        Text("Möchtest du diese Karte wirklich dauerhaft entfernen? Du kannst dies in den Einstellungen rückgängig machen")
                    },
                    onDismissRequest = { showConfirmationDialog = false },
                    confirmButton = {
                        Button({ show = false; showConfirmationDialog = false }) {
                            Text("Ja")
                        }
                    },
                    dismissButton = {
                        Button({ showConfirmationDialog = false }) {
                            Text("Nein")
                        }
                    })
        }

    }
}

public class WidgetScope(private var showState: MutableState<Boolean>) {
    fun hideWidget(){
        showState.value = false
    }
}

@Preview
@Composable
private fun WidgetPreview() {
    Widget("previewWidget") {
        Column{
            Heading("My Widget")
            Spacer(Modifier.height(10.dp))
            Text("This is an example widget for non crucial ui")
        }
    }
}