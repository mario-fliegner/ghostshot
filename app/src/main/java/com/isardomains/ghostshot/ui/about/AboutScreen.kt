package com.isardomains.ghostshot.ui.about

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.isardomains.ghostshot.ui.theme.GhostShotAppSurface
import com.isardomains.ghostshot.ui.theme.GhostShotTextSecondary

@Composable
fun AboutScreenRoute(
    onBack: () -> Unit
) {
    AboutScreenContent(
        versionName = BuildConfig.VERSION_NAME,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    versionName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val feedbackSubject = stringResource(R.string.about_feedback_subject)

    Scaffold(
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
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AboutHero()

            Spacer(modifier = Modifier.height(44.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.about_local_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostShotTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_no_account_required),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GhostShotTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = GhostShotTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.about_send_feedback),
                style = MaterialTheme.typography.labelMedium,
                color = GhostShotTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@isardomains.com")
                        putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun AboutHero() {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(GhostShotAppSurface),
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
                .size(72.dp)
                .testTag("about_app_icon")
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.about_app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = stringResource(R.string.about_description),
        style = MaterialTheme.typography.bodyMedium,
        color = GhostShotTextSecondary,
        textAlign = TextAlign.Center
    )
}
