package br.com.pocooptimizer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.pocooptimizer.ui.*
import br.com.pocooptimizer.ui.dashboard.DashboardScreen
import br.com.pocooptimizer.ui.cleaner.CleanerScreen
import br.com.pocooptimizer.ui.security.SecurityScreen
import br.com.pocooptimizer.ui.performance.PerformanceScreen
import br.com.pocooptimizer.ui.settings.SettingsScreen
import br.com.pocooptimizer.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.rgb(8,8,13))
        )
        setContent { PocoTheme { OptimizerApp() } }
    }
    @Composable private fun OptimizerApp(vm: OptimizerViewModel = viewModel()) {
        val s by vm.state.collectAsStateWithLifecycle()
        val prefs by vm.prefs.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        val last by vm.lastScan.collectAsStateWithLifecycle()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val haptics=LocalHapticFeedback.current
        val snack=remember { SnackbarHostState() }
        val lifecycle=LocalLifecycleOwner.current
        val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri!=null) vm.authorize(uri) else vm.message("Nenhuma pasta foi autorizada.") }
        val file=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.inspect(uri) }
        val authorize: () -> Unit = { try { folder.launch(null) } catch(e: Exception) { vm.message("Seletor de pastas indisponível neste aparelho.") } }
        val openSettings: (String,String) -> Unit = { action,name ->
            try { startActivity(Intent(action)); vm.actionOpened(name) }
            catch (_: Exception) {
                try { startActivity(Intent(Settings.ACTION_SETTINGS)); vm.actionOpened("Configurações gerais (alternativa para $name)") }
                catch (_: Exception) { vm.message("Tela de configurações indisponível.") }
            }
        }
        LaunchedEffect(lifecycle) {
            lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                vm.returnedFromSettings()
                while(isActive) { vm.refresh(); delay(15_000) }
            }
        }
        LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.message(null) } }
        Scaffold(snackbarHost={SnackbarHost(snack)},bottomBar={
            NavigationBar {
                val labels=listOf("Início","Limpeza","Segurança","Desemp.","Ajustes")
                val icons=listOf(Icons.Outlined.Dashboard,Icons.Outlined.CleaningServices,Icons.Outlined.Security,Icons.Outlined.Speed,Icons.Outlined.Settings)
                labels.forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],label)},label={Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)},alwaysShowLabel=true) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(s.busy) Surface(color=MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(s.progress,style=MaterialTheme.typography.bodyMedium)
                        AnalysisProgress(done=s.done,total=s.total,modifier=Modifier.fillMaxWidth())
                        if(!s.deleting) TextButton(onClick=vm::cancel) { Text("Cancelar operação") }
                    }
                }
                Box(Modifier.weight(1f)) {
                    val analyze: () -> Unit = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.scan() }
                    when(tab) {
                        0 -> DashboardScreen(s,last,analyze)
                        1 -> CleanerScreen(s,prefs,authorize,analyze,vm::select,vm::prepareDeletion)
                        2 -> SecurityScreen(s,{
                            try { file.launch(arrayOf("*/*")) } catch(e: Exception) { vm.message("Seletor de arquivos indisponível.") }
                        },analyze,{
                            try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage("com.android.vending")) }
                            catch (_: Exception) { vm.message("Abra a Play Store manualmente → perfil → Play Protect → Verificar.") }
                        }, { pkg ->
                            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$pkg"))); vm.actionOpened("Detalhes de $pkg") }
                            catch (_: Exception) { vm.message("Detalhes do app indisponíveis.") }
                        })
                        3 -> PerformanceScreen(s,openSettings)
                        4 -> SettingsScreen(s,prefs,history,vm::preference,authorize,vm::revoke,vm::clearLocal)
                    }
                }
            }
        }
        s.plan?.let { plan ->
            AlertDialog(onDismissRequest=vm::dismissPlan,title={Text("Confirmar exclusão permanente?")},
                text={ Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("${plan.selected.size} arquivos • ${bytes(plan.selected.sumOf { it.file.size })}. Esta operação pode não ser desfeita. A confirmação expira em 5 minutos.")
                    LazyColumn(Modifier.heightIn(max=280.dp)) { items(plan.selected) { item ->
                        Text(item.file.name,style=MaterialTheme.typography.titleSmall)
                        Note("${item.file.path} • ${bytes(item.file.size)}")
                        Spacer(Modifier.height(12.dp))
                    } }
                } },
                confirmButton={TextButton(onClick=vm::confirmDeletion) { Text("EXCLUIR ARQUIVOS",color=MaterialTheme.colorScheme.error) }},
                dismissButton={TextButton(onClick=vm::dismissPlan) { Text("Cancelar") }})
        }
    }
}
