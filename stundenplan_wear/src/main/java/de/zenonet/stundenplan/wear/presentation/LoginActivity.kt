/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package de.zenonet.stundenplan.wear.presentation

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.Tasks.await
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.wear.presentation.theme.StundenplanTheme


class LoginActivity : ComponentActivity() {
    lateinit var isShowingContinueOnPhoneAnimation: MutableState<Boolean>;
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val dataChangedListener by lazy { MyDataChangedListener(this) }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isShowingContinueOnPhoneAnimation.value = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //registerMessageListener();
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            isShowingContinueOnPhoneAnimation = remember {
                mutableStateOf(false)
            }
            //if (!isShowingContinueOnPhoneAnimation.value)
                WearApp(activity = this)
        }
    }

    fun startRemoteLogin(callback: ((Int) -> Unit)? = null) {

        Thread {
            val nodeClient = Wearable.getNodeClient(this);
            val capabilityClient = Wearable.getCapabilityClient(this);

            val connectedNodes = Tasks.await(nodeClient.connectedNodes)
            if (connectedNodes.isEmpty()) {
                callback?.invoke(1)
                Log.i(LogTags.Login, "No connected nodes available")
                return@Thread;
            }

            val capableNodes = Tasks.await(capabilityClient.getCapability("verify_remote_wolkenberg_stundenplan_phone_app", CapabilityClient.FILTER_REACHABLE)).nodes
            if(capableNodes.isEmpty()){
                callback?.invoke(2)
                return@Thread
            }



            val localNodeId = await(nodeClient.localNode).id

            val remoteIntent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("stpl://www.zenonet.de/stundenplan/remoteLogin/$localNodeId"));

            // Send the local node id
            //remoteIntent.putExtra("nodeId", await(nodeClient.localNode).id)


            try {
                val remoteActivityHelper = RemoteActivityHelper(this)
                val selectedNode = capableNodes.iterator().next()
                Log.i(LogTags.Login, "Starting remote login activity on ${selectedNode.displayName}")
                remoteActivityHelper.startRemoteActivity(remoteIntent, targetNodeId = selectedNode.id)
                showContinueOnPhone()
                isShowingContinueOnPhoneAnimation.value = true
            } catch (e: RemoteActivityHelper.RemoteIntentException) {
                callback?.invoke(2)
                return@Thread
            }
            callback?.invoke(0)

        }.start();
    }

    fun loginSucceeded() {
        val intent = Intent(this, WearTimeTableViewActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(dataChangedListener)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(dataChangedListener)
    }

    private fun showContinueOnPhone() {
        val confirmation = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(
                ConfirmationActivity.EXTRA_ANIMATION_TYPE,
                ConfirmationActivity.OPEN_ON_PHONE_ANIMATION
            )
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_DURATION_MILLIS, 2000)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, "Auf Telefon fortsetzen")
            addFlags(FLAG_ACTIVITY_NO_HISTORY)
        }
        launcher.launch(confirmation)
    }
}

@Composable
fun WearApp(activity: LoginActivity?) {
    StundenplanTheme {

        var status by remember {
            mutableIntStateOf(-1)
        }
        val listState = rememberScalingLazyListState()
        Scaffold(
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            val boxState = rememberSwipeToDismissBoxState();
            SwipeToDismissBox(state = boxState, onDismissed = {
                status = -1
            }) { isBackground ->
                val showBox = status != -1
                if(isBackground || !showBox){
                    ScalingLazyColumn(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {

                        item {
                            Text(text = "Login", textAlign = TextAlign.Center, fontSize = 21.sp)

                        }
                        item {
                            Text(
                                text = "Logge dich mit deinem Smartphone ein",
                                textAlign = TextAlign.Center
                            )

                        }
                        item {
                            Button(onClick = {
                                if (activity == null) return@Button;

                                activity.startRemoteLogin {
                                    status = it
                                }

                                // TODO: Show feedback (eg. no phone is connected)

                            }, Modifier.fillMaxWidth()) {
                                Text(
                                    "Am Smartphone vortfahren", fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        item {
                            Button(onClick = {
                                if (activity == null) return@Button;

                                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                    .putBoolean("showPreview", true).apply()
                                activity.loginSucceeded()
                            }, Modifier.fillMaxWidth()) {
                                Text(
                                    "Weiter zur Vorschau", fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    return@SwipeToDismissBox
                }

                if (status < 0) return@SwipeToDismissBox
                ScalingLazyColumn(Modifier.fillMaxSize()) {
                    when (status) {
                        0 -> {
                            item {
                                Text(
                                    "Warte auf Aktion auf Telefon...",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        1 -> {
                            item {
                                Text("Kein Smartphone verfügbar", textAlign = TextAlign.Center)
                            }
                        }

                        2 -> {
                            item {
                                Text(
                                    "App konnte nicht gestartet werden. Ist sie wirklich auf dem Smartphone installiert?",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp(null)
}