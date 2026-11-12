package dev.nagi.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.nagi.app.ui.library.AboutScreen
import dev.nagi.app.ui.theme.NagiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpenSourceLicensesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bundledNoticesIncludeNonApacheLicensesAndUpstreamNotices() {
        val assets = RuntimeEnvironment.getApplication().assets
        val notices = assets.open("THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() }
        assertTrue(notices.contains("org.jsoup:jsoup:1.18.3"))
        assertTrue(notices.contains("Copyright (c) 2009-2024 Jonathan Hedley"))
        assertTrue(notices.contains("Redistribution and use in source and binary forms"))
        assertTrue(notices.contains("Mozilla Public License Version 2.0"))
        assertTrue(notices.contains("Notices for Eclipse Jakarta Dependency Injection"))
    }

    @Test fun licenseDialogIncludesProjectLicenseAndThirdPartyNotices() {
        compose.setContent { NagiTheme("light") { AboutScreen {} } }
        compose.onNodeWithText("开源许可").performClick()
        compose.onNodeWithText("GNU GENERAL PUBLIC LICENSE", substring = true).assertExists()
        compose.onNodeWithText("THIRD-PARTY NOTICES", substring = true).assertExists()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("分享反馈信息").assertExists()
    }
}
