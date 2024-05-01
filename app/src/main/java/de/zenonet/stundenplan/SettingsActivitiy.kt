@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)

package de.zenonet.stundenplan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.preference.PreferenceManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.material.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.switchPreference

class SettingsActivitiy : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // If notifications are enabled but the permission is not
        if (PreferenceManager.getDefaultSharedPreferences(this@SettingsActivitiy)
                .getBoolean("showNotifications", false)
            && (ActivityCompat.checkSelfPermission(
                this@SettingsActivitiy,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            // Disable the preference
            PreferenceManager.getDefaultSharedPreferences(this@SettingsActivitiy).edit()
                .putBoolean("showNotifications", false).apply();
        }

        setContent {
            StundenplanTheme {
                // A surface container using the 'background' color from the theme


                View(this)

            }
        }
    }
    public fun closeActivity() {
        finish()
    }


    private fun showSettingDialog() {
        MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Material3)
            .setTitle("Notification Permutation")
            .setMessage("Hello, your computer has virus")
            .setPositiveButton("Ok") { _, _ ->
                val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun View(activity: SettingsActivitiy?) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(
                        onClick = { activity?.closeActivity() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },

                )
        },
    ) { paddingValues ->

        val notificationPermission = rememberPermissionState(
            permission = Manifest.permission.POST_NOTIFICATIONS
        )

        var permissionAlertNecessary by remember {
            mutableStateOf(false)
        }
        val requestPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            permissionAlertNecessary = !isGranted;
        }

        if (permissionAlertNecessary) {
            AlertDialog(
                onDismissRequest = { /* Do nothing */ },
                title = { Text("Berechtigung benötigt") },
                text = { Text("Die App benötigt eine Berechtigung, um Dir Benachrichtigungen zu senden.") },
                confirmButton = {
                    TextButton(onClick = {
                        val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", "de.zenonet.stundenplan", null)
                        intent.data = uri
                        startActivity(activity!!, intent, null)
                        permissionAlertNecessary = false;
                    }) {
                        Text("Einstellungen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        permissionAlertNecessary = false;
                    }) {
                        Text("Abbrechen")
                    }
                }
            )
        }



        SettingsView(Modifier.padding(paddingValues)) {

            preferenceCategory("df", title = { Text("Benachrichtigungen") })
            switchPreference(
                key = "showNotifications",
                defaultValue = false,
                title = { Text("Zeige Benachrichtigungen für die nächsten Stunden") },
                summary = {
                    LaunchedEffect(it) {
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermission.status.isGranted) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    Text(if (it) (if (notificationPermission.status.isGranted) "Ja" else "Fehler: Benachrichtigungs-Berechtigung nicht erteilt") else "Nein")
                }
            )
            preferenceCategory("jf", title = { Text("Formattierung") })
            switchPreference(
                key = "showTeacherFirstNameInitial",
                defaultValue = false,
                title = { Text(text = "Zeige den ersten Buchstaben von Lehrer-Vornamen") },
                summary = { Text(text = if (it) "Bsp: M. Storch" else "Bsp: Storch") }
            )
        }
    }
}

@Composable
fun SettingsView(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    ProvidePreferenceLocals {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            content()
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    StundenplanTheme {
        View(null)
    }
}