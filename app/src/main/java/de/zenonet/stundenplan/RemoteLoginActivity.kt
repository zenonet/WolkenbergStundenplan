package de.zenonet.stundenplan

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import de.zenonet.stundenplan.activities.LoginActivity
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.callbacks.AuthCodeRedeemedCallback
import de.zenonet.stundenplan.common.timetableManagement.TimeTableApiClient
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException


class RemoteLoginActivity : ComponentActivity() {
    private lateinit var intentLauncher: ActivityResultLauncher<Intent>

    var isPreview: Boolean = false;
    var isOnboarded: Boolean = false;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this@RemoteLoginActivity);
        isOnboarded = preferences.getBoolean("onboardingCompleted", false)
        isPreview = preferences.getBoolean("showPreview", false)

        intentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                if (data != null) {
                    val code = data.getStringExtra("code")
                    val apiClient = TimeTableApiClient()
                    apiClient.init(this)
                    apiClient.redeemOAuthCodeAsync(code, object:AuthCodeRedeemedCallback {


                        override fun authCodeRedeemed() {
                            try {
                                sendRefreshToken()
                            } catch (e: UserLoadException) {
                            }                        }

                        override fun errorOccurred(message: String?) {
                            TODO("Not yet implemented")
                        }
                    })
                }
            }
        }

        setContent {
            StundenplanTheme {
                // A surface container using the 'background' color from the theme
                App(this)
            }
        }
    }

    fun sendRefreshToken() {
        lifecycleScope.launch {
            val dataClient = Wearable.getDataClient(this@RemoteLoginActivity);

            try {
                val preferences =
                    PreferenceManager.getDefaultSharedPreferences(this@RemoteLoginActivity);

                val request: PutDataRequest

                if (preferences.getBoolean("showPreview", false)) {
                    request = PutDataMapRequest.create("/refreshtoken").apply {
                        dataMap.putBoolean("showPreview", true)
                        dataMap.putLong("time", Instant.now().epochSecond)
                    }
                        .asPutDataRequest()
                        .setUrgent()
                } else {
                    val refreshToken = preferences.getString("refreshToken", "") ?: return@launch

                    request = PutDataMapRequest.create("/refreshtoken").apply {
                        dataMap.putString("refreshToken", refreshToken)
                        dataMap.putBoolean("showPreview", false)
                        dataMap.putLong("time", Instant.now().epochSecond)
                    }
                        .asPutDataRequest()
                        .setUrgent()
                }
                val result = dataClient.putDataItem(request).await()

                Log.d(LogTags.Login, "DataItem saved: $result")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(LogTags.Login, "Saving DataItem failed: $exception")
            }
            Toast.makeText(this@RemoteLoginActivity, "Login sent!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    fun usePreviewOnWatch() {
        lifecycleScope.launch {

            val dataClient = Wearable.getDataClient(this@RemoteLoginActivity);

            val request = PutDataMapRequest.create("/refreshtoken").apply {
                dataMap.putString("refreshToken", "")
                dataMap.putBoolean("showPreview", true)
                dataMap.putLong("time", Instant.now().epochSecond)
            }
                .asPutDataRequest()
                .setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    fun loginOnWatch() {
        startLoginProcess()
    }

    private fun startLoginProcess() {
        intentLauncher.launch(Intent(this, LoginActivity::class.java))
    }
}

@Composable
fun App(activity: RemoteLoginActivity? = null) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        var deviceName: String by remember {
            mutableStateOf("not-available")
        }
        var nodeId: String by remember {
            mutableStateOf("null")
        }


        LaunchedEffect(null) {
            launch(Dispatchers.IO) {
                if (activity == null || activity.intent == null) return@launch;

                nodeId = activity.intent.data?.path?.split('/')?.last().toString();
                val connectedNodes = Tasks.await(Wearable.getNodeClient(activity).connectedNodes);

                for (node in connectedNodes) {
                    if (node.id != nodeId) continue;
                    deviceName = node.displayName
                }
            }
        }

        Column {
            Text("$deviceName versucht sich in deinen Stundenplan einzuloggen.")
            if (activity!!.isOnboarded) {
                if (activity.isPreview) {
                    Text("Möchtest du auf dem Smartwatch ebenfalls die Vorschau verwenden?")
                } else {
                    Text("Möchtest du dein Login von diesem Smartphone auf deine Smartwatch übernehmen?")
                }

            } else {
                Text("Du bist allerdings noch nicht eingeloggt. Möchtest du dich auf deiner Smartwatch anmelden oder die Vorschau verwenden?")
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp)
            )

            Row {
                if (!activity.isOnboarded || activity.isPreview) {

                    Button(onClick = {
                        // Send refresh token
                        activity.usePreviewOnWatch()

                    }) {
                        Text("Vorschau verwenden")
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(10.dp)
                    )

                    Button(onClick = {
                        activity.loginOnWatch()
                    }) {
                        Text("Auf Uhr einloggen")
                    }

                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(10.dp)
                    )
                }
                if (activity.isOnboarded && !activity.isPreview) {
                    Button(onClick = {
                        activity.sendRefreshToken()

                    }) {
                        Text("Login auf Uhr übernehmen")
                    }
                }

                Button(onClick = {
                    activity?.finishAndRemoveTask();
                }) {
                    Text("Abbrechen")
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainPreview() {
    StundenplanTheme {
        App()
    }
}