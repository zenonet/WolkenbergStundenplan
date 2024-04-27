package de.zenonet.stundenplan

import android.app.Activity
import android.app.RemoteAction
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException


class RemoteLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                val preferences = PreferenceManager.getDefaultSharedPreferences(this@RemoteLoginActivity);
                val refreshToken =
                    preferences.getString("refreshToken", "null") ?: return@launch

                val request = PutDataMapRequest.create("/refreshtoken").apply {
                    dataMap.putString("refreshToken", refreshToken)
                    dataMap.putLong("time", Instant.now().epochSecond)
                }
                    .asPutDataRequest()
                    .setUrgent()

                val result = dataClient.putDataItem(request).await()

                Log.d(Utils.LOG_TAG, "DataItem saved: $result")
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Log.d(Utils.LOG_TAG, "Saving DataItem failed: $exception")
            }
        }
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