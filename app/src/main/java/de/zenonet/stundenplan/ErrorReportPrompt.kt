package de.zenonet.stundenplan

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.zenonet.stundenplan.common.LogTags
import de.zenonet.stundenplan.common.StundenplanApplication
import de.zenonet.stundenplan.ui.theme.StundenplanTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Duration

@Composable
fun ErrorReportPromptDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
) {
    AlertDialog(

        title = {
            Text(text = "Fehler melden?")
        },
        text = {
            Text(buildAnnotatedString {
                appendLine("Ein Fehler ist aufgetreten. Der Fehler kann dem Entwickler gemeldet werden, damit das Problem gelöst werden kann.")
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    withLink(LinkAnnotation.Url("https://zenonet.de/stundenplan/PrivacyPolicy.html")) {
                        append("Welche Daten werden dabei übertragen?")
                    }
                }
            })
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Fehler melden")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

fun showDialog(view: ComposeView, reportConfirmedCallback: ReportConfirmed) {
    view.setContent {
        var showDialog by remember { mutableStateOf(true) }
        if (showDialog) {
            StundenplanTheme {
                ErrorReportPromptDialog(
                    onDismissRequest = {
                        //showDialog = false
                        (view.parent as ViewGroup).removeView(view)
                    },
                    onConfirmation = {
                        (view.parent as ViewGroup).removeView(view)
                        reportConfirmedCallback.onReportConfirmed();
                    }
                )
            }
        }
    }
}

interface ReportConfirmed {
    fun onReportConfirmed()
}

fun createWorkRequest() {
    val data = Data.Builder()
        .putString("payload", StundenplanApplication.ReportableError.toString())
        .build()

    val workRequest: OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(ErrorReportWorker::class.java)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .setInputData(data)
            .build()

    val workManager = WorkManager.getInstance(StundenplanApplication.application)

    workManager.enqueue(workRequest)
    Log.i(LogTags.BackgroundWork, "Created work request for error reporting")
}

class ErrorReportWorker(appContext: Context, val workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val payload = workerParams.inputData.getString("payload")
        if(payload == null) return Result.failure()

        val request = Request.Builder()
            .url(BuildConfig.errorReportUrl)
            .post(payload.toRequestBody())
            .build()

        try {

            val response = OkHttpClient().newCall(request).execute();
            return if (response.isSuccessful) Result.success() else Result.retry();
        } catch (_: IOException) {
            return Result.retry()
        }
    }
}