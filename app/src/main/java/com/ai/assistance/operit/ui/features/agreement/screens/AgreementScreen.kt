package com.ai.assistance.operit.ui.features.agreement.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.ui.features.agreement.XiaoheiAgreementContent
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit) {
        if (XiaoheiAgreementContent.usesXiaoheiContent(BuildConfig.COMMON_BASE)) {
                XiaoheiAgreementScreen(onContinue = onAgreementAccepted)
        } else {
                UpstreamAgreementScreen(onAgreementAccepted = onAgreementAccepted)
        }
}

@Composable
private fun XiaoheiAgreementScreen(onContinue: () -> Unit) {
        val copy =
                XiaoheiAgreementContent.copyFor(
                        commonBase = true,
                        commonStore = BuildConfig.COMMON_STORE
                )
        val context = LocalContext.current
        // COMMON_BASE packages the draft in src/common/assets. Missing file is a packaging
        // error; do not substitute the Operit 2026-07-15 agreement.
        val bundledDocument =
                remember(context) {
                        context.assets
                                .open(XiaoheiAgreementContent.BUNDLED_POLICY_ASSET)
                                .bufferedReader(StandardCharsets.UTF_8)
                                .use { it.readText() }
                }
        AgreementChrome(
                title = copy.title,
                subtitle = copy.subtitle,
                versionLabel = copy.versionLabel,
                continueLabel = copy.continueLabel,
                onContinue = onContinue
        ) {
                Text(
                        text = copy.notice,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                        text = copy.documentHeading,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = bundledDocument,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        }
}

@Composable
private fun UpstreamAgreementScreen(onAgreementAccepted: () -> Unit) {
        AgreementChrome(
                title = stringResource(R.string.agreement_title),
                subtitle = stringResource(R.string.agreement_subtitle),
                versionLabel =
                        stringResource(
                                R.string.agreement_version,
                                AgreementPreferences.CURRENT_AGREEMENT_VERSION
                        ),
                continueLabel = stringResource(R.string.agreement_accept),
                onContinue = onAgreementAccepted
        ) {
                Text(
                        text = stringResource(R.string.agreement_human_readable_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = stringResource(R.string.agreement_human_readable_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = stringResource(R.string.agreement_serious_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                val textColor = MaterialTheme.colorScheme.onSurfaceVariant
                val typography = MaterialTheme.typography.bodyMedium
                AndroidView(
                        factory = { context ->
                                android.widget.TextView(context).apply {
                                        setTextColor(textColor.toArgb())
                                        textSize = typography.fontSize.value
                                        val lineHeightInPixels =
                                                (typography.lineHeight.value *
                                                                context.resources.displayMetrics
                                                                        .scaledDensity)
                                                        .toInt()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                lineHeight = lineHeightInPixels
                                        } else {
                                                setLineSpacing(
                                                        lineHeightInPixels -
                                                                paint.fontMetricsInt.descent +
                                                                paint.fontMetricsInt.ascent
                                                                        .toFloat(),
                                                        1.0f
                                                )
                                        }
                                }
                        },
                        update = { textView ->
                                textView.text =
                                        HtmlCompat.fromHtml(
                                                textView.context.getString(
                                                        R.string.agreement_serious_content
                                                ),
                                                HtmlCompat.FROM_HTML_MODE_COMPACT
                                        )
                        },
                        modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = stringResource(R.string.agreement_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
        }
}

@Composable
private fun AgreementChrome(
        title: String,
        subtitle: String,
        versionLabel: String,
        continueLabel: String,
        onContinue: () -> Unit,
        content: @Composable ColumnScope.() -> Unit
) {
        val scrollState = rememberScrollState()
        var isButtonEnabled by remember { mutableStateOf(false) }
        var remainingSeconds by remember { mutableStateOf(5) }

        LaunchedEffect(Unit) {
                repeat(5) {
                        delay(1000)
                        remainingSeconds--
                }
                isButtonEnabled = true
        }

        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(16.dp)
                                .background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .fillMaxWidth()
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                shape = MaterialTheme.shapes.medium
                                        )
                                        .padding(16.dp)
                ) {
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                                content()
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                        onClick = onContinue,
                        enabled = isButtonEnabled,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor =
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        disabledContentColor =
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                )
                ) {
                        Text(
                                text =
                                        if (isButtonEnabled) continueLabel
                                        else
                                                stringResource(
                                                        R.string.agreement_wait,
                                                        remainingSeconds
                                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                }

                Spacer(modifier = Modifier.height(16.dp))
        }
}
