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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tasks.Tasks.await
import com.google.android.gms.wearable.Wearable
import de.zenonet.stundenplan.common.Utils
import de.zenonet.stundenplan.wear.presentation.theme.StundenplanTheme


class LoginActivity : ComponentActivity() {
    lateinit var isShowingContinueOnPhoneAnimation: MutableState<Boolean>;
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val dataChangedListener by lazy { MyDataChangedListener(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        //registerMessageListener();
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            isShowingContinueOnPhoneAnimation = remember {
                mutableStateOf(false)
            }
            if (!isShowingContinueOnPhoneAnimation.value)
                WearApp(activity = this)
        }
    }

    fun startRemoteLogin() {

        Thread {
            val nodeClient = Wearable.getNodeClient(this);
            val connectedNodes = Tasks.await(nodeClient.connectedNodes)
            if (connectedNodes.isEmpty()) {
                Log.i(Utils.LOG_TAG, "No connected nodes available")
                return@Thread;
            }

            val localNodeId = await(nodeClient.localNode).id
            Log.i(Utils.LOG_TAG, "Local node id is $localNodeId")

            val remoteIntent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse("stpl://www.zenonet.de/stundenplan/remoteLogin/$localNodeId"));

            // Send the local node id
            //remoteIntent.putExtra("nodeId", await(nodeClient.localNode).id)

            try {
                RemoteActivityHelper(this).startRemoteActivity(remoteIntent)
                showContinueOnPhone()
                isShowingContinueOnPhoneAnimation.value = true
            } catch (e: RemoteActivityHelper.RemoteIntentException) {

            }

        }.start();
    }

    fun loginSucceeded() {
        startActivity(Intent(this, WearTimeTableViewActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(dataChangedListener);
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
        startActivity(confirmation)
    }
}

@Composable
fun WearApp(activity: LoginActivity?) {
    StundenplanTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {

            ScalingLazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally,
                state = rememberScalingLazyListState(),
                modifier = Modifier
                    .padding(20.dp)
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

                        activity.startRemoteLogin();

                        // TODO: Show feedback (eg. no phone is connected)

                    }, Modifier.fillMaxWidth()) {
                        Text("Am Smartphone vortfahren", fontSize = 12.sp)
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