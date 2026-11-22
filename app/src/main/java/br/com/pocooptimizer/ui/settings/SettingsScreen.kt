package br.com.pocooptimizer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.BuildConfig
import br.com.pocooptimizer.data.*
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.utils.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun SettingsScreen(s: UiState, prefs: Preferences, history: List<History>, change: (Preferences) -> Unit,
    authorize: () -> Unit, revoke: (String) -> Unit, clear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var historyFilter by remember { mutableStateOf("Todos") }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Apagar dados locais?")},
        text={Text("Remove histórico, preferências e acessos persistentes a pastas. Seus arquivos pessoais não serão excluídos.")},
        confirmButton={TextButton(onClick={confirmClear=false;clear()}) { Text("Apagar dados locais") }},
        dismissButton={TextButton(onClick={confirmClear=false}) { Text("Cancelar") }})
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Ajustes",style=MaterialTheme.typography.headlineMedium) }
        item { Panel("Preferências da análise") {
            Row { Switch(modifier=Modifier.semantics { contentDescription="Calcular duplicatas por SHA-256" },checked=prefs.duplicates,onCheckedChange={change(prefs.copy(duplicates=it))},enabled=!s.busy); Text("Calcular duplicatas por SHA-256",modifier=Modifier.padding(12.dp)) }
            Text("Arquivo grande: a partir de ${prefs.largeMb} MiB")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(50,100,500).forEach { mb -> FilterChip(selected=prefs.largeMb==mb,onClick={change(prefs.copy(largeMb=mb))},label={Text("$mb MiB")},enabled=!s.busy) } }
            Text("Arquivo antigo: mais de ${prefs.oldDays} dias")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(30,90,180).forEach { days -> FilterChip(selected=prefs.oldDays==days,onClick={change(prefs.copy(oldDays=days))},label={Text("$days dias")},enabled=!s.busy) } }
            Note("Análises são iniciadas por você. Nenhum serviço permanente ou exclusão agendada.")
        } }
        item { Panel("Pastas autorizadas") {
            Button(onClick=authorize,enabled=!s.busy) { Text("Adicionar pasta") }
            if(s.roots.isEmpty()) Note("Nenhuma pasta autorizada")
            s.roots.forEach { root -> Note(android.net.Uri.decode(root)); TextButton(onClick={revoke(root)},enabled=!s.busy) { Text("Revogar acesso") } }
        } }
        item { Panel("Privacidade e informações") {
            Text("Poco Optimizer ${BuildConfig.VERSION_NAME}")
            Note("Versão de teste independente, sem afiliação à Xiaomi. Android 9 ou superior. Sem root, anúncios, rastreadores, telemetria ou permissão de internet.")
            Note("Arquivos e hashes são processados localmente. O histórico armazena nomes, caminhos e resultados no espaço privado do app, com limite de 200 registros. Backup do Android desativado. Uma cópia temporária de APK é apagada após a inspeção; resíduos de interrupções são removidos ao limpar os dados locais.")
            Note("Permissões de pastas persistem até revogação, desinstalação ou limpeza dos dados. Provedores de nuvem escolhidos pelo usuário podem baixar arquivos por conta própria. Não há serviço de reputação conectado.")
            OutlinedButton(onClick={confirmClear=true},enabled=!s.busy) { Text("Limpar dados locais do app") }
        } }
        item { Text("Histórico",style=MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Todos","Limpezas","Análises").forEach { name -> FilterChip(selected=historyFilter==name,onClick={historyFilter=name},label={Text(name)}) } }
        }
        val filtered=history.filter { when(historyFilter) { "Limpezas" -> it.kind in setOf("Limpeza","Limpeza iniciada","Arquivo"); "Análises" -> it.kind in setOf("Análise","Arquivo analisado"); else -> true } }
        if(filtered.isEmpty()) item { Note("Nenhum registro neste filtro") }
        items(filtered) { h ->
            var expanded by remember(h) { mutableStateOf(false) }
            Panel("${h.kind} • ${date(h.time)}") {
                Text(h.summary)
                TextButton(onClick={expanded=!expanded}) { Text(if(expanded) "Ocultar" else "Ver relatório") }
                if(expanded) Note(h.details)
            }
        }
    }
}
