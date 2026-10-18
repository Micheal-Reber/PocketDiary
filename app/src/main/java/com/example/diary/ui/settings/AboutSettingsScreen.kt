package com.example.diary.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.diary.BuildConfig
import com.example.diary.R
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing

private const val GitHubRepositoryUrl = "https://github.com/Micheal-Reber/PocketDiary"

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openRepository() {
        val opened = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(GitHubRepositoryUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (!opened) {
            showToast(context, context.getString(R.string.about_open_failed))
        }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard {
                Column(Modifier.padding(Spacing.l)) {
                    Text(
                        "PocketDiary",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.l))

            SettingsGroupCard {
                Column(Modifier.padding(Spacing.l)) {
                    Text(
                        stringResource(R.string.about_github_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        stringResource(R.string.about_github_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.l))
                    OutlinedButton(onClick = { openRepository() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.padding(end = Spacing.s)
                        )
                        Text(stringResource(R.string.about_open_github))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.l))

            SettingsGroupCard {
                Column(Modifier.padding(Spacing.l)) {
                    Text(
                        stringResource(R.string.about_offline_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        stringResource(R.string.about_offline_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.l),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Made by Xuan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(BottomBarContentInset))
        }
    }
}