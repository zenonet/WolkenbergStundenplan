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
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.common.timetableManagement.TimeTableApiClient
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException


class RemoteLoginActivity : ComponentActivity() {
    private lateinit var intentLauncher: ActivityResultLauncher<Intent>;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                if (data != null) {
                    val code = data.getStringExtra("code")
                    val apiClient = TimeTableApiClient()
                    apiClient.init(this)
                    apiClient.redeemOAuthCodeAsync(code) {
                        try {
                            sendRefreshToken(true)
                        } catch (e: UserLoadException) {
                        }
                    }
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

    fun sendRefreshToken(preventRecursion: Boolean = false) {
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
                    val refreshToken =
                        preferences.getString("refreshToken", "") ?: return@launch
                    val showPreview =
                        preferences.getBoolean("showPreview", false)

                    if (refreshToken == "" && !showPreview) {
                        if (!preventRecursion)
                            startLoginProcess()
                        return@launch
                    }

                    request = PutDataMapRequest.create("/refreshtoken").apply {
                        dataMap.putString("refreshToken", refreshToken)
                        dataMap.putBoolean("showPreview", false)
                        dataMap.putLong("time", Instant.now().epochSecond)
                    }
                        .asPutDataRequest()
                        .setUrgent()
                }
                val result = dataClient.putDataItem(request).await()

                Log.d(Utils.LOG_TAG, "DataItem saved: $result")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(Utils.LOG_TAG, "Saving DataItem failed: $exception")
            }
            Toast.makeText(this@RemoteLoginActivity, "Login sent!", Toast.LENGTH_SHORT).show()
            finish()
        }
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
            Text(
                "$deviceName versucht sich in deinen Stundenplan einzuloggen.\n" +
                        "Möchtest du dein Login von diesem Smartphone auf alle deine Wearable-Geräte (Smartwatches) übernehmen?"
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp)
            )

            Row {
                Button(onClick = {
                    if (activity == null) return@Button;

                    // Send refresh token
                    activity.sendRefreshToken()

                    /*
                    Thread {
                        val nodeClient = Wearable.getNodeClient(activity);
                        val connectedNodes = Tasks.await(nodeClient.connectedNodes)
                        if (connectedNodes.isEmpty()) {
                            Log.i(Utils.LOG_TAG, "No connected nodes available")
                            return@Thread;
                        }

                        val encodedRefreshToken = URLEncoder.encode(refreshToken);
                        val remoteIntent = Intent(Intent.ACTION_VIEW)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .setData(Uri.parse("https://www.zenonet.de/stundenplan/refreshToken/$encodedRefreshToken"));


                        RemoteActivityHelper(activity)
                            .startRemoteActivity(
                                targetIntent = remoteIntent,
                                targetNodeId = nodeId                            )
                        Log.i(Utils.LOG_TAG, "Remote activity started")
                    }.start();*/

                }) {
                    Text("Zulassen")
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                )

                Button(onClick = {
                    activity?.finishAndRemoveTask();
                }) {
                    Text("Ablehnen")
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