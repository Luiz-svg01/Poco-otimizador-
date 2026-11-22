package br.com.pocooptimizer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.pocooptimizer.ui.AnalysisProgress
import br.com.pocooptimizer.ui.PocoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressRegressionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun progressRemainsSafeWhenTotalBecomesUnknownBeforeRecomposition() {
        val counters = mutableStateOf<Pair<Int, Int?>>(2 to 4)
        compose.setContent {
            PocoTheme { AnalysisProgress(counters.value.first,counters.value.second,Modifier.testTag("progress")) }
        }
        val transitions = listOf(2 to 4, 0 to null, 1 to 2, 0 to 0, 4 to 4, 0 to null)
        repeat(5) {
            transitions.forEach { (done,total) ->
                compose.runOnIdle { counters.value = done to total }
                // Access semantics between stage updates, the path that crashed in Material3.
                val expected = if(total == null || total <= 0) ProgressBarRangeInfo.Indeterminate
                    else ProgressBarRangeInfo(done.toFloat()/total,0f..1f)
                compose.onNodeWithTag("progress").assertRangeInfoEquals(expected)
            }
        }
    }
}
