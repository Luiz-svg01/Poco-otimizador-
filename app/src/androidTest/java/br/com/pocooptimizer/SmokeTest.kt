package br.com.pocooptimizer

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        val dir=File(compose.activity.filesDir,"screenshots").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun navigationAndRealScanWithoutFolderPermission() {
        compose.onNodeWithText("POCO OPTIMIZER").assertIsDisplayed()
        screenshot("dashboard")
        compose.onNodeWithText("ANALISAR DISPOSITIVO").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithText("Cancelar operação").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Limpeza",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Autorizar pasta").assertIsDisplayed()
        screenshot("cleaner")
        compose.onNodeWithText("Segurança",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Central de segurança").assertIsDisplayed()
        screenshot("security")
        compose.onNodeWithText("Ajustes",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Preferências da análise").assertIsDisplayed()
    }
}
