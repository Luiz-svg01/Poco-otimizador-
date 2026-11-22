package br.com.pocooptimizer.ui.performance

import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.utils.*

@Composable fun PerformanceScreen(s: UiState, open: (String,String) -> Unit) {
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Desempenho",style=MaterialTheme.typography.headlineMedium) }
        item { Panel("Indicadores do Android") {
            s.device?.let { d ->
                Meter("Armazenamento utilizado",d.totalStorage-d.freeStorage,d.totalStorage,"${bytes(d.totalStorage-d.freeStorage)} de ${bytes(d.totalStorage)} • volume de dados")
                Meter("RAM em uso",d.totalRam-d.freeRam,d.totalRam,"${bytes(d.freeRam)} disponíveis de ${bytes(d.totalRam)}")
                Text("Bateria: ${d.battery?.let { "$it%" } ?: "indisponível"}")
                Note("Carregando: ${when(d.charging) { true -> "sim"; false -> "não"; null -> "não informado" }}")
                Note("Temperatura: ${d.temperature?.let { "$it °C" } ?: "não informada"}")
                Note("Economia de energia: ${if(d.powerSave) "ativada" else "desativada"}")
                Note("${d.model} • ${d.android}")
            } ?: Note("Indicadores indisponíveis")
            Note("Atualização a cada 15 segundos enquanto a tela está visível. Memória disponível e temperatura variam; não medem a velocidade da CPU.")
        } }
        if(s.performance.isEmpty()) item { Note("Nenhuma condição de atenção identificada nos indicadores disponíveis.") }
        items(s.performance) { FindingCard(it) }
        item { Panel("Ações seguras") {
            Button(onClick={open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS,"Armazenamento")}) { Text("Abrir armazenamento") }
            OutlinedButton(onClick={open(Settings.ACTION_BATTERY_SAVER_SETTINGS,"Economia de bateria")}) { Text("Revisar bateria") }
            OutlinedButton(onClick={open(Settings.ACTION_APPLICATION_SETTINGS,"Aplicativos")}) { Text("Revisar aplicativos") }
            Note("Revise arquivos grandes, brilho da tela e apps desnecessários. No HyperOS, use as opções oficiais de bateria para gerenciar atividade em segundo plano. Restrições podem atrasar notificações.")
            Note("Nenhum app será encerrado pelo Poco Optimizer. Fechar apps repetidamente pode consumir mais bateria ao reabri-los. Não é possível atribuir lentidão a um app sem medições adicionais.")
            Note("Aberturas de configurações e leituras após o retorno ficam no histórico. O app não afirma que uma configuração foi alterada nem inventa percentuais de melhoria.")
        } }
    }
}
