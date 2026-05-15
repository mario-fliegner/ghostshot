package com.isardomains.ghostshot.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.isardomains.ghostshot.BuildConfig
import com.isardomains.ghostshot.R
import com.isardomains.ghostshot.ui.theme.GhostShotAboutActionText
import com.isardomains.ghostshot.ui.theme.GhostShotAboutBodyText
import com.isardomains.ghostshot.ui.theme.GhostShotAboutCardSurface
import com.isardomains.ghostshot.ui.theme.GhostShotAboutFooterText
import com.isardomains.ghostshot.ui.theme.GhostShotAboutIconSurface
import com.isardomains.ghostshot.ui.theme.GhostShotAboutTitleText
import kotlinx.coroutines.launch

@Composable
fun AboutScreenRoute(
    onBack: () -> Unit
) {
    AboutScreenContent(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    feedbackIntentLauncher: ((Intent) -> Boolean)? = null
) {
    val context = LocalContext.current
    val feedbackEmail = stringResource(R.string.about_feedback_email)
    val feedbackSubject = stringResource(R.string.about_feedback_subject)
    val noEmailAppMessage = stringResource(R.string.about_feedback_no_email_app)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val openFeedbackIntent = feedbackIntentLauncher ?: { intent: Intent ->
        startFeedbackIntent(context, intent)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AboutHeroCard()
                AboutFooter(
                    versionName = versionName,
                    versionCode = versionCode,
                    onFeedbackClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$feedbackEmail")
                            putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                        }
                        if (!openFeedbackIntent(intent)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(noEmailAppMessage)
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun startFeedbackIntent(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

@Composable
private fun AboutHeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GhostShotAboutCardSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(GhostShotAboutIconSurface),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            setImageResource(R.mipmap.ic_launcher)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("about_app_icon")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.about_app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = GhostShotAboutTitleText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotAboutBodyText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.about_local_device),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotAboutBodyText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.about_no_account_required),
                style = MaterialTheme.typography.bodyMedium,
                color = GhostShotAboutBodyText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AboutFooter(
    versionName: String,
    versionCode: Int,
    onFeedbackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = GhostShotAboutCardSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.about_version, versionName, versionCode),
                style = MaterialTheme.typography.bodySmall,
                color = GhostShotAboutFooterText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(GhostShotAboutIconSurface)
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(onClick = onFeedbackClick)
                    .testTag("about_send_feedback")
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.about_send_feedback),
                    style = MaterialTheme.typography.labelMedium,
                    color = GhostShotAboutActionText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
