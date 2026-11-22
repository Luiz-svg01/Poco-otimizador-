package br.com.pocooptimizer.ui.cleaner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.data.Preferences
import br.com.pocooptimizer.domain.*
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.utils.*

@Composable fun CleanerScreen(s: UiState, prefs: Preferences, authorize: () -> Unit, scan: () -> Unit, select: (String) -> Unit, prepare: () -> Unit) {
    var filter by remember { mutableStateOf("Revisar") }
    val report=s.storage
    val duplicateIds=remember(report) { report?.duplicates.orEmpty().flatMap { it.files }.map { it.uri }.toSet() }
    val groupsByUri=remember(report) { report?.duplicates.orEmpty().flatMap { g -> g.files.map { it.uri to g } }.toMap() }
    val now=remember(report) { System.currentTimeMillis() }
    val visible=remember(report,filter,prefs) { report?.files.orEmpty().filter {
        when(filter) { "Todos" -> true; "Duplicatas" -> it.uri in duplicateIds; else -> Analysis.categories(it,now,prefs.largeMb*1024L*1024,prefs.oldDays).isNotEmpty() }
    }.sortedByDescending { it.size } }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Limpeza inteligente",style=MaterialTheme.typography.headlineMedium) }
        item { Panel("Você decide o que remover") {
            Note("Arquivos antigos ou grandes não são lixo. Revise nome e localização. Somente pastas concedidas pelo seletor oficial são acessadas; o Android pode bloquear a raiz de Downloads, Android/data e Android/obb.")
            Button(onClick=authorize,enabled=!s.busy) { Text("Autorizar pasta") }
            OutlinedButton(onClick=scan,enabled=!s.busy) { Text("Analisar pastas e dispositivo") }
            Note("${s.roots.size} pastas autorizadas. Cache privado de outros apps não é acessível.")
        } }
        s.deletion?.let { result ->
            item { Panel("Resultado real da limpeza") {
                Text("${result.deleted} arquivos excluídos • ${bytes(result.bytes)} contabilizados")
                Note("${result.items.size-result.deleted} ignorados ou com erro. Bytes dos arquivos cuja exclusão foi confirmada; o volume livre pode variar e provedores podem manter lixeira.")
            } }
            items(result.items) { item -> Note("${item.name}: ${item.reason}") }
        }
        if(report!=null) {
            item { Panel("Resultado nas áreas autorizadas") {
                Text("${report.files.size} arquivos • ${report.duplicates.size} grupos idênticos")
                Note("Duplicatas: mesmo tamanho e SHA-256. Mantenha pelo menos uma cópia; grupos podem estar incompletos fora das áreas autorizadas.")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Revisar","Duplicatas","Todos").forEach { name -> FilterChip(selected=filter==name,onClick={filter=name},label={Text(name)}) }
                }
                val count=s.selected.size
                val size=report.files.filter { it.uri in s.selected }.sumOf { it.size.coerceAtLeast(0) }
                Button(onClick=prepare,enabled=count>0&&!s.busy,modifier=Modifier.fillMaxWidth()) { Text("Revisar $count selecionados • ${bytes(size)}") }
            } }
            items(report.warnings) { Note(it) }
            if(visible.isEmpty()) item { Note("Nenhum arquivo encontrado neste filtro. Isso não significa que todas as áreas do aparelho foram analisadas.") }
            items(visible,key={it.uri}) { f ->
                Panel(f.name) {
                    Note(f.path)
                    Note("${bytes(f.size)} • ${date(f.modified)}")
                    val tags=Analysis.categories(f,now,prefs.largeMb*1024L*1024,prefs.oldDays)
                    if(tags.isNotEmpty()) Note(tags.joinToString(" • "))
                    val group=groupsByUri[f.uri]
                    if(group!=null) {
                        Text("Duplicata • grupo ${group.hash.take(12)} • ${group.files.size} cópias",color=MaterialTheme.colorScheme.primary)
                        Note("Outras localizações: " + group.files.filter { it.uri != f.uri }.take(5).joinToString("; ") { it.path })
                        if(group.files.size>6) Note("Veja as demais cópias no filtro Duplicatas.")
                    }
                    Row {
                        Checkbox(checked=f.uri in s.selected,onCheckedChange={select(f.uri)},enabled=!s.busy && f.canDelete && f.size>=0)
                        Text(if(f.canDelete && f.size>=0) "Selecionar para revisão" else "Exclusão indisponível neste provedor",modifier=Modifier.padding(top=12.dp))
                    }
                }
            }
        } else if(!s.busy) item { Note("Execute uma análise para listar arquivos reais.") }
    }
}
