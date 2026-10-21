package `in`.daasavaani.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun completePreparationGatesNativeReading() {
        val context = compose.activity
        assertEquals("in.daasavaani.app", context.packageName)
        assertEquals("http://10.0.2.2:5173/demo/latest.json", context.getString(R.string.descriptor_url))
        compose.onNodeWithText("ರಾಮ ಕೃಷ್ಣ").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.prepare_library)).performClick()
        compose.waitUntil(timeoutMillis = 60000) {
            compose.onAllNodesWithText("ಕನ್ನಡ ಓದು — ಪರೀಕ್ಷೆ ೧").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("ಕನ್ನಡ ಓದು — ಪರೀಕ್ಷೆ ೧").performClick()
        compose.onNodeWithText("ರಾಮ ಕೃಷ್ಣ").assertIsDisplayed()
    }
}
