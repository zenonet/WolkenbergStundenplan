package de.zenonet.stundenplan.nonCrucialUi.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.zhanghai.compose.preference.rememberPreferenceState

@Composable
fun Widget(
    widgetKey: String,
    modifier: Modifier = Modifier,
    closingOverride: (() -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)
) {
    var showConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var show by rememberPreferenceState("nonCrucialUi.$widgetKey", true)
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
                    content()
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
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
                    .size(36.dp)
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