package com.masselis.tpmsadvanced.interfaces

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masselis.tpmsadvanced.interfaces.composable.HomeTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
internal class DebugUpdateCheckerTest {
    @get:Rule
    val compose = createAndroidComposeRule<RootActivity>()

    @Test
    fun aboutOpensManualReleaseChecker() {
        openChecker(HomeTags.Overflow.about)
    }

    @Test
    fun settingsOpensManualReleaseChecker() {
        openChecker(HomeTags.Overflow.settings)
    }

    private fun openChecker(menuTag: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(HomeTags.Actions.overflow))
        compose.onNodeWithTag(HomeTags.Actions.overflow).performClick()
        compose.onNodeWithTag(menuTag).performClick()
        compose.onNodeWithText("Check for TyrePing updates").performScrollTo().performClick()
        compose.onNodeWithText("TyrePing updates").assertIsDisplayed()
        compose.onNodeWithText("Stable").assertIsDisplayed()
        compose.onNodeWithText("Include pre-releases").assertIsDisplayed()
        compose.onNodeWithText("Press Check now to contact GitHub.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check now").assertIsDisplayed()
    }
}
