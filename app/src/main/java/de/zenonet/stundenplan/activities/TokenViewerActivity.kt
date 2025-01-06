package de.zenonet.stundenplan.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.preference.PreferenceManager
import de.zenonet.stundenplan.ExpandableCard
import de.zenonet.stundenplan.R
import de.zenonet.stundenplan.TokenViewerViewModel
import de.zenonet.stundenplan.common.timetableManagement.TimeTableManager
import de.zenonet.stundenplan.homework.ViewModelFactory
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import de.zenonet.stundenplan.ui.theme.Typography

class TokenViewerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timeTableManager = TimeTableManager()
        timeTableManager.init(this)

        val viewModelFactory: () -> TokenViewerViewModel = {
            TokenViewerViewModel(
                timeTableManager,
                PreferenceManager.getDefaultSharedPreferences(this)
            )
        }

        enableEdgeToEdge()
        setContent {
            StundenplanTheme {

                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    TopAppBar(title = {
                        Text("Token-Ansicht")
                    },
                        navigationIcon = {
                            IconButton({
                                this@TokenViewerActivity.finish()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                            }
                        })
                }) { innerPadding ->
                    val vm =
                        viewModel<TokenViewerViewModel>(factory = ViewModelFactory(viewModelFactory))
                    TokenViewer(vm, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun TokenViewer(vm: TokenViewerViewModel, modifier: Modifier = Modifier) {
    Column(modifier.padding(15.dp).verticalScroll(rememberScrollState())) {
        LaunchedEffect(null) {
            vm.loadRefreshToken()
            vm.loadUserData()
        }

        ExpandableCard(header = {
            Text("Was ist das?", style = MaterialTheme.typography.headlineMedium)

        }) {
            Text("Hier kannst du Dir die Tokens anzeigen lassen, die von der App verwendet werden, um auf deinen Stundenplan zuzugreifen. " +
                    "Dein ursprünglicher Token wurde generiert, als Du dich angemeldet hast. Seitdem wird der refreshToken jedensmal ersetzt, wenn du die App öffnest." +
                    "Aus dem refreshToken wird kann dann ein accessToken generiert werden. Mit diesem können Stundenplan-Daten abgefragt werden.")
        }

        Spacer(Modifier.height(20.dp))


        if (vm.refreshToken != null) {
            Text("Aktueller RefreshToken:")
            TokenView(vm.refreshToken!!)
        } else {
            Text("Kein refreshToken verfügbar")
        }
        Spacer(Modifier.height(10.dp))
        Button({
            vm.generateAccessToken()
        }) {
            Text("Access-Token generieren")
        }
        if (vm.accessTokenErrorMessage != null) {
            if (vm.accessToken != null) {
                Text("Access token:")
                TokenView(vm.accessToken!!)
            } else if (vm.accessTokenErrorMessage!!.isEmpty()) {
                Text("Wird geladen...")
            } else {
                Text(vm.accessTokenErrorMessage!!)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Weitere Informationen von API", style = Typography.headlineSmall)
        Text("Dein Name: ${vm.fullName}")
        Text("Deine ID: ${if(vm.studentId != -1)  vm.studentId else "nicht verfügbar"}")
        Text("Dein User-Typ: ${vm.userType}")
    }
}

@Composable
fun TokenView(token: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier.padding(5.dp)) {
        Text(
            text = token,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 12.sp,
            style = androidx.compose.ui.text.TextStyle()
        )
    }

    val clipboardManager = LocalClipboardManager.current
    val localContext = LocalContext.current
    IconButton({
        Toast.makeText(localContext, "In Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
        clipboardManager.setText(AnnotatedString(token))
    }) {
        Icon(
            painter = painterResource(id = R.drawable.baseline_content_copy_24),
            contentDescription = "copy token"
        )
    }
}