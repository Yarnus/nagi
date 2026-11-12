package dev.nagi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nagi.app.data.AccountState
import dev.nagi.app.ui.components.AccountAvatar
import dev.nagi.app.ui.components.CapsuleButton
import dev.nagi.app.ui.components.CapsuleStyle
import dev.nagi.app.ui.components.CardGroupPosition
import dev.nagi.app.ui.components.FlatCard
import dev.nagi.app.ui.components.GoldTagBadge
import dev.nagi.app.ui.components.LoadingLine
import dev.nagi.app.ui.components.NagiHeader
import dev.nagi.app.ui.components.NagiIcons
import dev.nagi.app.ui.components.Notice
import dev.nagi.app.ui.components.SectionCardItem
import dev.nagi.app.ui.theme.NagiThemeTokens
import dev.nagi.app.ui.theme.Space
import dev.nagi.app.viewmodel.AccountIdentity
import dev.nagi.app.viewmodel.AccountViewModel

@Composable
fun AccountsScreen(
    account: AccountState,
    vm: AccountViewModel,
    back: () -> Unit,
    login: () -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(Modifier.fillMaxSize()) {
        NagiHeader("账号管理", back = back)
        LazyColumn(
            contentPadding = PaddingValues(vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "current-status") {
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
                        Text(
                            text = account.userId?.let { "当前登录 UID：$it" } ?: "当前：访客模式（本地数据独立保留）",
                            style = typography.topicTitle,
                            color = colors.textPrimary,
                        )
                        account.error?.let { err ->
                            Text(err, style = typography.metadata, color = colors.destructive)
                        }
                    }
                }
            }
            if (account.accounts.isNotEmpty()) {
                itemsIndexed(account.accounts, key = { _, uid -> uid }) { index, uid ->
                    val current = uid == account.userId
                    SectionCardItem(CardGroupPosition.at(index, account.accounts.size)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !account.busy && !current) { vm.switchAccount(uid) }
                                .heightIn(min = 52.dp)
                                .padding(horizontal = Space.lg, vertical = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = if (current) "UID $uid · 当前账号" else "切换至 UID $uid",
                                style = typography.topicTitle,
                                color = if (current) colors.goldText else colors.textPrimary,
                            )
                            if (current) {
                                GoldTagBadge(text = "当前")
                            } else {
                                Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    CapsuleButton(
                        text = if (account.userId == null) "登录 NGA" else "添加登录",
                        onClick = login,
                        enabled = !account.busy,
                        style = CapsuleStyle.SolidGold,
                    )
                    if (account.userId != null) {
                        CapsuleButton(
                            text = "退出当前账号",
                            onClick = vm::logout,
                            enabled = !account.busy,
                            style = CapsuleStyle.Destructive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OwnProfileScreen(
    identity: AccountIdentity,
    back: () -> Unit,
    retry: () -> Unit,
    content: (String) -> Unit,
) {
    val colors = NagiThemeTokens.colors
    val typography = NagiThemeTokens.typography
    Column(Modifier.fillMaxSize()) {
        NagiHeader("我的档案", back = back)
        if (identity.loading) LoadingLine()
        identity.error?.let { Notice(it, retry) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            FlatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                shape = NagiThemeTokens.shapes.cardGroup,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    AccountAvatar(identity, Modifier.size(48.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = identity.user?.name ?: "UID ${identity.userId.orEmpty()}",
                            style = typography.sectionTitle,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "UID ${identity.userId.orEmpty()}",
                            style = typography.metadata,
                            color = colors.textSecondary,
                        )
                        identity.user?.let { user ->
                            Text(
                                text = "发帖数 ${user.posts}",
                                style = typography.metadata,
                                color = colors.goldText,
                            )
                        }
                    }
                }
            }
            Column {
                SectionCardItem(CardGroupPosition.First) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { content("topics") }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("我的主题", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
                SectionCardItem(CardGroupPosition.Last) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { content("replies") }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = Space.lg, vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("我的回复", style = typography.topicTitle, color = colors.textPrimary)
                        Icon(NagiIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.textSecondary)
                    }
                }
            }
        }
    }
}
