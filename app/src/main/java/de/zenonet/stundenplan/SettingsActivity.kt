@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalPermissionsApi::class)

package de.zenonet.stundenplan

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.glance.appwidget.updateAll
import androidx.preference.PreferenceManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import de.zenonet.stundenplan.activities.TokenViewerActivity
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.glance.TimetableWidget
import de.zenonet.stundenplan.nonCrucialUi.PreviewPermissionState
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import me.zhanghai.compose.preference.switchPreference

class SettingsActivity : ComponentActivity() {
    lateinit var tokenViewerLauncher: ActivityResultLauncher<Intent>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge();

        tokenViewerLauncher = this.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ){
            if(it.resultCode == RESULT_OK){
                startTokenViewer()
            }
        }

        // If notifications are enabled but the permission is not
        if ((ActivityCompat.checkSelfPermission(
                this@SettingsActivity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            // Disable the preference
            PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity).edit()
                .putBoolean("showNotifications", false).apply();
            PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity).edit()
                .putBoolean("showChangeNotifications", false).apply();
        }

        setContent {
            StundenplanTheme {
                // A surface container using the 'background' color from the theme


                View(this)

            }
        }
    }
    fun startTokenViewer(){
        val intent = Intent(this@SettingsActivity, TokenViewerActivity::class.java)
        startActivity(intent)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun View(activity: SettingsActivity?) {
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(),
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if(activity == null) return@IconButton

                            coroutineScope.launch {
                                TimetableWidget().updateAll(activity)
                                (StundenplanApplication.application as StundenplanPhoneApplication).scheduleUpdateRepeating()
                                activity.finish()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },

                )
        },
    ) { paddingValues ->


        val notificationPermission =
            if (LocalView.current.isInEditMode) PreviewPermissionState() else rememberPermissionState(
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
                onDismissRequest = {

                },
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

            if (activity != null && PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean("showPreview", false)
            ) {
                preference(
                    "unpreview",
                    {
                        Text("Demo-Modus verlassen")
                    },
                    summary = {
                        Text("Um echte Stundenpläne zu sehen musst du dich einloggen.")
                    },
                    widgetContainer = {
                        Button(onClick = {
                            val intent = Intent(activity, OnboardingActivity::class.java)
                            activity.startActivity(intent)
                            activity.finish()
                        }) {
                            Text("Einloggen")
                        }
                    }
                )
            }

            preferenceCategory("df", title = { Text("Benachrichtigungen") })
            switchPreference(
                key = "showChangeNotifications",
                defaultValue = false,
                title = { Text("Zeige Benachrichtigungen wenn sich Dein Stundenplan ändert") },
                summary = {
                    LaunchedEffect(it) {
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermission.status.isGranted) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    Text(if (it) (if (notificationPermission.status.isGranted) "Benachrichtigungen werden angezeigt." else "Fehler: Benachrichtigungs-Berechtigung nicht erteilt") else "Keine Benachrichtigungen werden angezeigt.")
                }
            )
            switchPreference(
                key = "showNotifications",
                defaultValue = false,
                title = { Text("Zeige Status-Benachrichtigungen für die nächsten Stunden") },
                summary = {
                    LaunchedEffect(it) {
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermission.status.isGranted) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    Text(if (it) (if (notificationPermission.status.isGranted) "Benachrichtigungen werden angezeigt." else "Fehler: Benachrichtigungs-Berechtigung nicht erteilt") else "Keine Benachrichtigungen werden angezeigt.")
                }
            )

            preferenceCategory("jf", title = { Text("Formattierung") })
            switchPreference(
                key = "showTeacherFirstNameInitial",
                defaultValue = false,
                title = { Text(text = "Zeige den ersten Buchstaben von Lehrer-Vornamen") },
                summary = { Text(text = if (it) "Bsp: M. Storch" else "Bsp: Storch") }
            )
            switchPreference(
                key = "showLeadingZerosInRooms",
                defaultValue = true,
                title = { Text(text = "Zeige führende Nullen in Raum-Bezeichnungen") },
                summary = { Text(text = if (it) "Bsp: D07" else "Bsp: D7") }
            )
            switchPreference(
                key = "combineSameSubjectLessons",
                defaultValue = true,
                title = { Text(text = "Verbinde Doppelstunden") },
                summary = { Text(text = if (it) "Kein Spalt zwischen Stunden einer Doppelstunde wird angezeigt" else "Spalt zwischen Stunden einer Doppelstunde wird angezeigt") }
            )
            preferenceCategory("ik", title = {Text("Andere")})
            switchPreference(
                "showWhenLocked",
                defaultValue = true,
                title = {Text("Stundenplan über Sperrbildschirm anzeigen")},
                summary = { Text(text = if (it) "Stunenplan wird über Sperrbildschirm angezeigt" else "Stunenplan wird nicht über Sperrbildschirm angezeigt") }
            )
            preferenceCategory("gn", title = {Text("Für Entwickler")})
            if(BuildConfig.DEBUG) {
                switchPreference(
                    "alwaysParse",
                    defaultValue = false,
                    title = { Text("Demenzmodus (Debug only)") },
                    summary = { Text(text = if (it) "Stundenplan wird immer aus Rohdaten geparst" else "Stunenplan nach Parsing gecached") }
                )
            }
            preference("showToken", {
                Text("RefreshToken anzeigen")
            },
                summary = {
                    Text("Zeige den API-refreshToken an, der zur Authentifizierung verwendet wird")
                },
                onClick = {
                    if(activity == null) return@preference
                    val confirmationIntent = activity.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("", "");
                    if(confirmationIntent != null)
                        activity.tokenViewerLauncher.launch(confirmationIntent)
                    else
                        activity.startTokenViewer()
                })
/*            switchPreference(
                key = "useCursedLayout",
                defaultValue = false,
                title = { Text(text = "Cursed Layout") },
            )*/

            /*
                        // TODO:
                        preferenceCategory("jg", title = { Text("Fach Vorwarnung") })
                        switchPreference(
                            key = "showSubjectWarning",
                            defaultValue = false,
                            title = { Text(text = "Zeige vor der Schule Benachrichtigungen für spezielle Fächer") },
                            summary = { Text(text = if (it) "Bsp: M. Storch" else "Bsp: Storch") }
                        )
            */
            item {
                Row(Modifier.padding(15.dp, 15.dp, 15.dp, 2.dp).fillMaxWidth()) {
                    Text(buildAnnotatedString {
                        withLink(LinkAnnotation.Url("https://github.com/zenonet/WolkenbergStundenplan/tree/v${BuildConfig.VERSION_NAME}")) {
                            appendLine("v${BuildConfig.VERSION_NAME}${if(BuildConfig.DEBUG) " (debug)" else ""}")
                        }
                        append("Diese App ist Open Source. ")
                        val style = SpanStyle(textDecoration = TextDecoration.Underline)
                        withStyle(style = style) {
                            withLink(LinkAnnotation.Url("https://github.com/zenonet/WolkenbergStundenplan")) {
                                append("Quellcode")
                            }
                        }
                    }, fontSize = 12.sp)
                }
            }
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
fun SettingsPreview() {
    StundenplanTheme {
        View(null)
    }
}