package br.com.pocooptimizer.ui.security

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.utils.bytes

@Composable fun SecurityScreen(s: UiState, inspect: () -> Unit, scan: () -> Unit, store: () -> Unit, appSettings: (String) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text("Central de segurança",style=MaterialTheme.typography.headlineMedium) }
        item { Panel("Análise transparente e offline") {
            Note("Heurísticas indicam atenção ou risco potencial. Não são diagnóstico de vírus. Esta versão não possui fonte externa para confirmar ameaças nem faz uma varredura completa de malware.")
            Button(onClick=scan,enabled=!s.busy) { Text("Analisar dispositivo") }
            OutlinedButton(onClick=inspect,enabled=!s.busy) { Text("Inspecionar APK ou arquivo") }
            Note("O arquivo é lido localmente. APKs de até 256 MiB têm metadados internos consultados sem instalação ou execução. Nenhum arquivo ou hash é enviado à internet.")
        } }
        s.fileAudit?.let { audit -> item { Panel(audit.name) {
            Note("${audit.mime} • ${bytes(audit.size)}")
            Text("SHA-256",style=MaterialTheme.typography.titleSmall)
            SelectionContainer { Text(audit.hash,style=MaterialTheme.typography.bodySmall) }
            Note(audit.details)
        } } }
        item { Panel("Google Play Protect") {
            Note("Abra a Play Store → foto de perfil → Play Protect → Verificar. Não há API pública usada aqui para iniciar ou ler essa verificação; esta orientação não representa integração.")
            OutlinedButton(onClick=store) { Text("Abrir Play Store") }
        } }
        s.security?.let { report ->
            item { Panel("Verificações realizadas") { report.checks.forEach { Note(it) }; report.warnings.forEach { Note(it) } } }
            if(report.findings.isEmpty() && report.apps.all { it.findings.isEmpty() }) item { Note("Nenhum problema identificado nas verificações disponíveis. Isso não garante ausência de ameaças.") }
            items(report.findings) { FindingCard(it) }
            items(report.apps,key={it.pkg}) { app ->
                Panel(app.name) {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        app.icon?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.size(40.dp)) }
                        Column { Note(app.pkg); Text("${app.findings.size} observações • ${app.permissions.size} permissões declaradas") }
                    }
                    Note("Instalador: ${app.source}")
                    TextButton(onClick={expanded=if(expanded==app.pkg) null else app.pkg}) { Text(if(expanded==app.pkg) "Ocultar detalhes" else "Ver permissões, assinatura e riscos") }
                    if(expanded==app.pkg) {
                        app.findings.forEach { FindingCard(it) }
                        if(app.permissions.isEmpty()) Note("Nenhuma permissão declarada consultável.")
                        app.permissions.forEach { Note(it) }
                        Text("Certificados de assinatura • SHA-256")
                        if(app.signatures.isEmpty()) Note("Não disponíveis")
                        app.signatures.forEach { SelectionContainer { Text(it,style=MaterialTheme.typography.bodySmall) } }
                        OutlinedButton(onClick={appSettings(app.pkg)}) { Text("Revisar app nas configurações") }
                    }
                }
            }
        }
    }
}
