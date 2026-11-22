package br.com.pocooptimizer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.domain.*

val Purple = Color(0xFF7C3AED)
@Composable fun PocoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFFB99AFF), onPrimary=Color(0xFF200C38),
        primaryContainer=Color(0xFF321747), onPrimaryContainer=Color(0xFFF5F5F7),
        background=Color(0xFF08080D), surface=Color(0xFF13131D), onSurface=Color(0xFFF5F5F7),
        onSurfaceVariant=Color(0xFFB9B9C3), error=Color(0xFFFF8888)),content=content)
}
@Composable fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium); content()
        }
    }
}
@Composable fun Note(text: String) { Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun FindingCard(f: Finding) {
    Panel(f.title) {
        Text(f.risk.label,color=if(f.risk==Risk.POTENTIAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text("Observação: ${f.observation}")
        Note("Recomendação: ${f.recommendation}")
        Note("Confiança: ${f.confidence}")
    }
}
@Composable fun Meter(label: String, used: Long, total: Long, detail: String) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,style=MaterialTheme.typography.titleSmall)
        if(total>0) LinearProgressIndicator(progress={ (used.toDouble()/total).toFloat().coerceIn(0f,1f) }, modifier=Modifier.fillMaxWidth())
        Note(detail)
    }
}

/** Capture a value for this composition. Material may invoke progress later (draw/semantics),
 * after the scanner has switched from a known total to an indeterminate stage. */
@Composable fun AnalysisProgress(done: Int, total: Int?, modifier: Modifier = Modifier) {
    val fraction = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
    if (fraction == null) LinearProgressIndicator(modifier=modifier)
    else LinearProgressIndicator(progress={ fraction },modifier=modifier)
}
