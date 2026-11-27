package dev.nagi.app.ui.library

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.UpdateCheckStatus
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.LocalAppUpdate
import dev.nagi.app.ui.components.NagiConfirmDialog
import dev.nagi.app.ui.components.NagiDialogActions
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.components.safeBottomPadding
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ExternalLinkTarget(val copyButtonText: String, val clipLabel: String) {
    Feedback("复制反馈链接", "GitHub 反馈"),
    Release("复制发布页链接", "GitHub Releases"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onCheckUpdate: () -> Unit = {},
    onResetUpdateStatus: () -> Unit = {},
    back: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    val appUpdate = LocalAppUpdate.current
    DisposableEffect(Unit) {
        onDispose { onResetUpdateStatus() }
    }
    var licenses by rememberSaveable { mutableStateOf(false) }
    var unavailableUri by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailableTarget by rememberSaveable { mutableStateOf<ExternalLinkTarget?>(null) }
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val feedback = remember(version) {
        feedbackUri(version, Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}")
    }
    val latestRelease = remember { latestReleaseUri() }
    fun openExternal(uri: Uri, target: ExternalLinkTarget) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            unavailableUri = null
            unavailableTarget = null
        } catch (_: ActivityNotFoundException) {
            unavailableUri = uri.toString()
            unavailableTarget = target
        }
    }
    Column(Modifier.fillMaxSize()) {
        NagiHeader("关于与反馈", back = back)
        if (appUpdate.checking) {
            LoadingLine()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Space.lg, bottom = Space.lg + safeBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
            FlatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                shape = NagiThemeTokens.shapes.cardGroup,
            ) {
                Column(
                    modifier = Modifier.padding(Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Nagi $version", style = typography.sectionTitle, color = colors.textPrimary)
                            if (appUpdate.hasUpdate) {
                                GoldTagBadge("新版本")
                            }
                        }
                        CapsuleButton(
                            text = if (appUpdate.checking) "检查中…" else "检查更新",
                            onClick = onCheckUpdate,
                            icon = NagiIcons.Refresh,
                            enabled = !appUpdate.checking,
                            style = CapsuleStyle.Neutral,
                        )
                    }
                    when (appUpdate.manualStatus) {
                        UpdateCheckStatus.IDLE -> Unit
                        UpdateCheckStatus.CHECKING -> Text(
                            text = "正在检查 GitHub 最新版本…",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                        UpdateCheckStatus.UP_TO_DATE -> Text(
                            text = "当前已是最新版本",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                        UpdateCheckStatus.UPDATE_AVAILABLE -> Text(
                            text = "发现新版本 ${appUpdate.latestTag.orEmpty()}，请前往发布页下载",
                            style = typography.metadata,
                            color = colors.goldText,
                        )
                        UpdateCheckStatus.FAILED -> Text(
                            text = "检查更新失败，请检查网络后重试",
                            style = typography.metadata,
                            color = colors.destructive,
                        )
                    }
                    Text(
                        text = "在 GitHub 提交问题或功能建议，需要登录 GitHub。将附带 App 版本、Android 版本和设备型号，请勿包含账号凭据或私信内容。",
                        style = typography.body,
                        color = colors.textSecondary,
                    )
                }
            }
            Column {
                SectionCardItem(CardGroupPosition.First) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openExternal(feedback, ExternalLinkTarget.Feedback) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("前往 GitHub 反馈", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
                SectionCardItem(CardGroupPosition.Middle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openExternal(latestRelease, ExternalLinkTarget.Release) }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("查看最新版本", style = typography.topicTitle, color = colors.textPrimary)
                            if (appUpdate.hasUpdate) {
                                GoldTagBadge(appUpdate.latestTag ?: "新版本")
                            }
                        }
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
                SectionCardItem(CardGroupPosition.Last) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { licenses = true }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("开源许可", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
            }
        }
    }
    val blockedUri = unavailableUri
    val blockedTarget = unavailableTarget
    if (blockedUri != null && blockedTarget != null) {
        val clearUnavailable = {
            unavailableUri = null
            unavailableTarget = null
        }
        dev.nagi.app.ui.components.NagiConfirmDialog(
            onDismissRequest = clearUnavailable,
            message = "无法打开 GitHub，请安装浏览器或复制链接后打开。",
            confirmLabel = blockedTarget.copyButtonText,
            onConfirm = {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText(blockedTarget.clipLabel, blockedUri))
                clearUnavailable()
            },
            confirmStyle = CapsuleStyle.SelectedGold,
            dismissLabel = "关闭",
            onDismiss = clearUnavailable,
        )
    }
    if (licenses) {
        val sections by produceState<List<String>?>(initialValue = null, key1 = context) {
            value = withContext(Dispatchers.IO) {
                val full = listOf("LICENSE", "THIRD_PARTY_NOTICES.txt").joinToString("\n\n") { asset ->
                    context.assets.open(asset).bufferedReader().use { it.readText() }
                }
                splitLicenseSections(full)
            }
        }
        dev.nagi.app.ui.components.NagiDialog(
            onDismissRequest = { licenses = false },
            modifier = Modifier.heightIn(max = 600.dp),
        ) {
            Text("开源许可", style = typography.sectionTitle, color = colors.textPrimary)
            val loadedSections = sections
            if (loadedSections == null) {
                LoadingLine()
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    loadedSections.forEach { section ->
                        Text(
                            text = section,
                            style = typography.body,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            NagiDialogActions(
                confirmLabel = "关闭",
                onConfirm = { licenses = false },
                confirmStyle = CapsuleStyle.Neutral,
            )
        }
    }
}

private fun splitLicenseSections(raw: String, maxChunkChars: Int = 2_000): List<String> {
    if (raw.isBlank()) return emptyList()
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (paragraph in raw.split("\n\n")) {
        if (paragraph.isBlank()) continue
        if (current.isNotEmpty() && current.length + paragraph.length + 2 > maxChunkChars) {
            chunks += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) current.append("\n\n")
        current.append(paragraph)
    }
    if (current.isNotEmpty()) chunks += current.toString()
    return chunks
}
