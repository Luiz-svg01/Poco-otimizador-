package br.com.pocooptimizer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.utils.*

@Composable fun DashboardScreen(s: UiState, lastScan: Long, analyze: () -> Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Shield,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(42.dp))
                Column { Text("POCO OPTIMIZER",style=MaterialTheme.typography.titleLarge); Note("Seu dispositivo, sob controle.") }
            }
        }
        item { Panel("Visão geral") {
            Text(s.device?.model ?: "Consultando dispositivo…",style=MaterialTheme.typography.headlineSmall)
            Note(s.device?.android ?: "")
            s.device?.let { d ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    if(d.totalStorage>0) CircularProgressIndicator(progress={ 1f-d.freeStorage.toFloat()/d.totalStorage },modifier=Modifier.size(72.dp),strokeWidth=8.dp)
                    Column { Text("${bytes(d.freeStorage)} livres",style=MaterialTheme.typography.titleLarge); Note("${bytes(d.totalStorage-d.freeStorage)} utilizados") }
                }
                Meter("RAM disponível",d.freeRam,d.totalRam,"${bytes(d.freeRam)} de ${bytes(d.totalRam)} • varia com o uso")
                Note("Bateria: ${d.battery?.let { "$it%" } ?: "indisponível"} • ${d.temperature?.let { "$it °C" } ?: "temperatura não informada"}")
            }
            Note("Última análise concluída: ${date(lastScan)}")
        } }
        item {
            Button(onClick=analyze,enabled=!s.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("ANALISAR DISPOSITIVO") }
        }
        item { Panel("Análise completa") {
            Note("Verifica dados do aparelho, pastas autorizadas, duplicatas por SHA-256, apps visíveis e condições de desempenho. Nenhum arquivo é excluído e nenhuma configuração é alterada.")
            OutlinedButton(onClick=analyze,enabled=!s.busy,modifier=Modifier.fillMaxWidth()) { Text("INICIAR ANÁLISE COMPLETA") }
            Note("${s.roots.size} pasta(s) autorizada(s). Autorize pastas em Limpeza ou Ajustes.")
        } }
        if(s.scanned) item { Panel("Relatório consolidado") {
            Text("Limpeza: ${s.storage?.files?.size ?: 0} arquivos consultados; ${s.storage?.duplicates?.size ?: 0} grupos de duplicatas.")
            Text("Segurança: ${s.security?.apps?.size ?: 0} apps visíveis; ${s.security?.let { it.findings.size+it.apps.sumOf { a -> a.findings.size } } ?: 0} observações para revisão.")
            Text("Desempenho: ${s.performance.size} condições que merecem atenção.")
            Note("Abra cada central para revisar os resultados e as limitações. Ausência de alertas não certifica que o dispositivo está seguro.")
        } }
        items(s.scanWarnings) { Note(it) }
    }
}
