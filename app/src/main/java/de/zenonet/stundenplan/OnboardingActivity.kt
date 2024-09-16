package de.zenonet.stundenplan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import de.zenonet.stundenplan.activities.LoginActivity
import de.zenonet.stundenplan.activities.TimeTableViewActivity
import de.zenonet.stundenplan.common.models.User
import de.zenonet.stundenplan.common.models.UserType
import de.zenonet.stundenplan.common.timetableManagement.TimeTableApiClient
import de.zenonet.stundenplan.common.timetableManagement.UserLoadException
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import kotlinx.coroutines.launch


class OnboardingActivity : ComponentActivity() {

    var onLoginCompleted: ((ActivityResult) -> Unit)? = null;
    var intentLauncher: ActivityResultLauncher<Intent>? = null;

    var userData: User? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            onLoginCompleted?.invoke(result)
        }


        enableEdgeToEdge()
        setContent {
            StundenplanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    OnboardingScreen(this, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(activity: OnboardingActivity?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
    ) {
        val pagerState = rememberPagerState(1) {
            3
        }
        var isNextButtonEnabled by rememberSaveable {
            mutableStateOf(true)
        }

        val coroutineScope = rememberCoroutineScope()

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .weight(1f)
                .padding(15.dp)
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(30.dp))
                when (it) {
                    0 -> {
                        Header("Willkommen!")
                        Text("Diese App ist ein inoffizieller Client für den Stundenplan des Wolkenberg Gymnasiums in Michendorf.")
                        Spacer(Modifier.height(10.dp))

                        ExpandableCard(header = { Text("Datenschutz-Informationen") }) {
                            Text(
                                "Diese App sammelt keine persönlichen Daten von Dir. Allerdings muss sie, um Deinen Stundenplan abzurufen, mit Microsoft und der Stundenplan API kommunizieren." +
                                        "Welche Daten bei der Nutzung der Stundenplan API erhoben werden ist jedoch unbekannt, " +
                                        "da der offizielle Stundenplan-Client die Notwendigkeit einer Datenschutzerklärung gänzlich ingoriert."
                            )
                        }

                    }

                    1 -> {
                        Header("Login")
                        Text("Damit die App deine Stundenplan-Daten laden kann, musst du dich mit deinem Schul-Microsoft-Account einloggen.")

                        Spacer(Modifier.height(15.dp))

                        Text("Deine Login-Daten werden nicht gespeichert.\nDer Entwickler dieser App keinen Zugriff darauf.", fontSize = 10.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            // Go to the next page since that auto-triggers the login process
                            coroutineScope.launch {
                                pagerState.scrollToPage(pagerState.currentPage+1)
                            }
                        }) {
                            Text("Login")
                        }

/*                        ExpandableCard(header = { Text("Wie funktioniert das?") }) {
                            Text(
                                "Beim Login in der offiziellen WebApp (via Microsoft) entsteht ein OAuth Code. " +
                                        "Dies ist ein einmaliges Token, das der inoffizielle Client abfängt. " +
                                        "Damit kann er dann ein refreshToken generieren, aus dem er immer wieder neue accessTokens " +
                                        "generieren kann.\nDiese accessTokens ermöglichen es, den Stundenplan einzusehen.\n" +
                                        "Bei der Authentifizierung erhält der inoffizielle Stundenplan lediglich diese Tokens, NICHT DEIN PASSWORT."
                            )
                        }
 */
                        Spacer(Modifier.height(80.dp))

                        Header("Vorschau-Version")
                        Text("Wenn du die App nur ausprobieren willst, ohne dich einzuloggen, kannst du Dir auch einen vorgefertigten Stundenplan ansehen.")
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            if(activity == null) return@Button

                            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                .putBoolean("showPreview", true).apply()

                            val mainActivityIntent = Intent(activity, TimeTableViewActivity::class.java)
                            activity.startActivity(mainActivityIntent)
                            activity.finish()

                        }) {
                            Text("Weiter zur Vorschau")
                        }
                    }

                    2 -> {
                        var username by rememberSaveable {
                            mutableStateOf("")
                        }
                        var loginState by remember {
                            mutableIntStateOf(0)
                        }

                        if (activity != null)
                            LaunchedEffect(null) {
                                isNextButtonEnabled = false;
                                activity.onLoginCompleted = { result ->
                                    if (result.resultCode == Activity.RESULT_OK) {
                                        val data: Intent? = result.data
                                        if (data != null) {
                                            val apiClient = TimeTableApiClient()
                                            apiClient.init(activity)
                                            val code = data.getStringExtra("code")
                                            apiClient.redeemOAuthCodeAsync(code) {
                                                try {
                                                    activity.userData = apiClient.getUser()
                                                    username = activity.userData!!.fullName
                                                    loginState = 2
                                                } catch (e: UserLoadException) {
                                                    loginState = -2
                                                }
                                            }
                                            loginState = 1
                                        } else {
                                            loginState = -1
                                        }
                                    }
                                }


                                activity.intentLauncher!!.launch(
                                    Intent(
                                        activity,
                                        LoginActivity::class.java
                                    )
                                )
                            }

                        Header("Login")

                        when (loginState) {
                            0 -> {
                                Text("Du wirst angemeldet...")
                            }

                            -1 -> {
                                Text("Es ist ein Fehler beim Login aufgetreten")
                            }

                            -2 -> {
                                Text("Nutzerdaten konnten nicht gelesen werden")
                            }

                            1 -> {
                                Text("Nutzerinformationen werden geladen...")
                            }

                            2 -> {
                                Text("Wilkommen $username!")
                                Spacer(Modifier.height(10.dp))
                                if(activity?.userData?.type == UserType.teacher){
                                    Text("Ups, Sie sind ein Lehrer, aufgrund der Annahme, es würde niemals ein Lehrer diese Anwendung benutzen, " +
                                            "ist sie bis jetzt auch nicht darauf ausgelegt. Es könnte zu Fehlern kommen.")
                                    Spacer(Modifier.height(15.dp))
                                }
                                Text("Beim ersten Mal kann das Laden des Stundenplans etwas länger dauern", textAlign = TextAlign.Center)

                                Spacer(Modifier.height(30.dp))
                                Button(onClick = {
                                    if(activity == null) return@Button

                                    PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                        .putBoolean("onboardingCompleted", true).apply()

                                    // Open timetable view activity
                                    val mainActivityIntent =
                                        Intent(activity, TimeTableViewActivity::class.java)
                                    activity.startActivity(mainActivityIntent)
                                    activity.finish()
                                }){
                                    Text("Weiter")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(text: String) {
    Text(text, fontSize = 22.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun ExpandableCard(
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(modifier) {
        var isExpanded by remember {
            mutableStateOf(false)
        }
        OutlinedButton(
            onClick = { isExpanded = !isExpanded },
            Modifier.fillMaxWidth(),
            border = BorderStroke(0.dp, Color.Transparent)
        ) {
            header()
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(Modifier.padding(15.dp)) {
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnBoardingScreenPreview() {
    StundenplanTheme {
        OnboardingScreen(null)
    }
}