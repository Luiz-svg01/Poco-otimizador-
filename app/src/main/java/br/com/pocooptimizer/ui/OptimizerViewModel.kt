package br.com.pocooptimizer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.pocooptimizer.data.*
import br.com.pocooptimizer.domain.*
import br.com.pocooptimizer.repository.OptimizerRepository
import br.com.pocooptimizer.scanner.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class UiState(val device: DeviceSnapshot? = null, val storage: StorageReport? = null,
    val security: SecurityReport? = null, val performance: List<Finding> = emptyList(),
    val busy: Boolean = false, val deleting: Boolean = false, val stage: Int = 0, val progress: String = "",
    val done: Int = 0, val total: Int? = null, val message: String? = null,
    val selected: Set<String> = emptySet(), val plan: DeletePlan? = null, val deletion: DeleteResult? = null,
    val fileAudit: FileAudit? = null, val roots: List<String> = emptyList(), val scanned: Boolean = false,
    val scanWarnings: List<String> = emptyList())

class OptimizerViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = OptimizerRepository(application)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    val prefs = repo.local.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val history = repo.local.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lastScan = repo.local.lastScan.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    private var operation: Job? = null
    private var pendingAction: Pair<String, DeviceSnapshot?>? = null
    init { refresh(); refreshRoots() }
    private fun refreshRoots() { _state.update { it.copy(roots = repo.storage.roots().map(Uri::toString)) } }
    fun refresh() { viewModelScope.launch(Dispatchers.IO) {
        try { val d = repo.device.snapshot(); _state.update { it.copy(device = d, performance = Analysis.performance(d)) } }
        catch(e: Exception) { message("Indicadores indisponíveis: ${e.message}") }
    } }
    private fun progress(stage: Int, text: String, done: Int = 0, total: Int? = null) {
        _state.update { it.copy(stage=stage,progress=text,done=done,total=total) }
    }
    fun scan() {
        if(state.value.busy) return
        _state.update { it.copy(busy=true, scanned=false, storage=null, security=null, selected=emptySet(), plan=null, deletion=null, scanWarnings=emptyList()) }
        operation = viewModelScope.launch(Dispatchers.IO) {
            val warnings = mutableListOf<String>()
            try {
                progress(0,"Consultando dispositivo")
                try { val d = repo.device.snapshot(); _state.update { it.copy(device=d) } }
                catch(e: Exception) { warnings += "Dispositivo: ${e.message}" }
                ensureActive(); progress(1,"Analisando pastas autorizadas")
                try {
                    val storage = repo.storage.scan(prefs.value.duplicates) { text, done, total -> progress(1,text,done,total) }
                    _state.update { it.copy(storage=storage) }
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { warnings += "Armazenamento não concluído: ${e.message}" }
                ensureActive(); progress(2,"Verificando segurança")
                try {
                    val security = repo.security.scan { done,total -> progress(2,"Apps consultados: $done/$total",done,total) }
                    _state.update { it.copy(security=security) }
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) { warnings += "Segurança não concluída: ${e.message}" }
                ensureActive(); progress(3,"Consolidando diagnóstico")
                val findings = state.value.device?.let(Analysis::performance).orEmpty()
                _state.update { it.copy(performance=findings,scanWarnings=warnings) }
                val s = state.value
                repo.local.add(History(System.currentTimeMillis(),"Análise", "${s.storage?.files?.size ?: 0} arquivos • ${s.security?.apps?.size ?: 0} apps visíveis",
                    "Limpeza: ${s.storage?.duplicates?.size ?: 0} grupos de duplicatas.\n" +
                    "Segurança: ${(s.security?.findings.orEmpty() + s.security?.apps.orEmpty().flatMap { it.findings }).joinToString("; ") { it.title }}\n" +
                    "Desempenho: ${findings.joinToString("; ") { it.title }}\n" +
                    (warnings + s.storage?.warnings.orEmpty() + s.security?.warnings.orEmpty()).joinToString("\n")), true)
                _state.update { it.copy(scanned=true) }; progress(4,"Análise concluída",4,4)
            } catch(e: CancellationException) {
                message("Análise cancelada. Resultados parciais não são uma análise completa.")
            } catch(e: Exception) { message("Não foi possível concluir: ${e.message}") }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
    fun cancel() { if(!state.value.deleting) operation?.cancel() }
    fun authorize(uri: Uri) { if(state.value.busy) return; _state.update { it.copy(busy=true,progress="Salvando acesso",total=null) }; viewModelScope.launch(Dispatchers.IO) {
        try { repo.storage.grant(uri); refreshRoots(); _state.update { it.copy(storage=null,selected=emptySet(),plan=null,scanned=false) }; message("Pasta autorizada. Inicie uma nova análise.") }
        catch(e: Exception) { message("Acesso não concedido: ${e.message}") }
        finally { _state.update { it.copy(busy=false) } }
    } }
    fun revoke(uri: String) { if(state.value.busy) return; _state.update { it.copy(busy=true,progress="Revogando acesso",total=null) }; viewModelScope.launch(Dispatchers.IO) {
        try { repo.storage.revoke(Uri.parse(uri)); refreshRoots(); _state.update { it.copy(storage=null,selected=emptySet(),plan=null,scanned=false) } }
        catch(e: Exception) { message("Não foi possível revogar: ${e.message}") }
        finally { _state.update { it.copy(busy=false) } }
    } }
    fun select(id: String) {
        val s = state.value
        if(s.busy || s.plan != null) return
        val selected = if(id in s.selected) s.selected - id else s.selected + id
        if(!Analysis.preservesCopies(selected,s.storage?.duplicates.orEmpty())) { message("Mantenha uma cópia de cada grupo de duplicatas."); return }
        _state.update { it.copy(selected=selected) }
    }
    fun prepareDeletion() {
        val s = state.value
        if(s.busy || s.storage == null || s.selected.isEmpty()) return
        _state.update { it.copy(busy=true) }
        operation = viewModelScope.launch(Dispatchers.IO) {
            try {
                progress(0,"Validando arquivos para confirmação")
                val plan = repo.files.prepare(s.storage,s.selected,System.currentTimeMillis())
                _state.update { it.copy(plan=plan) }
            } catch(e: CancellationException) { message("Preparação cancelada; nada foi excluído.") }
            catch(e: Exception) { message(e.message ?: "Falha na preparação") }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
    fun dismissPlan() { if(!state.value.deleting) _state.update { it.copy(plan=null) } }
    // Invoked only by the explicit confirmation dialog. No scan path can call this.
    fun confirmDeletion() {
        val plan = state.value.plan ?: return
        if(state.value.busy) return
        _state.update { it.copy(plan=null,busy=true,deleting=true,progress="Excluindo arquivos confirmados",total=null) }
        operation = viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                repo.local.add(History(System.currentTimeMillis(),"Limpeza iniciada","${plan.selected.size} arquivos confirmados", "Se não houver registro final, a operação foi interrompida; faça nova análise.\n" + plan.selected.joinToString("\n") { it.file.path }))
                var processed = 0
                val result = repo.files.execute(plan,System.currentTimeMillis()) { item ->
                    processed++
                    progress(0,"Arquivos processados: $processed/${plan.selected.size}",processed,plan.selected.size)
                    repo.local.add(History(System.currentTimeMillis(),"Arquivo", if(item.deleted) "Excluído: ${item.name}" else "Ignorado: ${item.name}", "${item.path}\n${item.bytes} bytes contabilizados. ${item.reason}"))
                }
                repo.local.add(History(System.currentTimeMillis(),"Limpeza", "${result.deleted} excluídos • ${result.bytes} bytes", result.items.joinToString("\n") { "${it.path}: ${it.reason}" }))
                _state.update { it.copy(deletion=result, storage=null, selected=emptySet(),scanned=false) }
            } catch(e: Exception) {
                _state.update { it.copy(storage=null,selected=emptySet(),scanned=false) }
                message("Limpeza interrompida: ${e.message}. Confira o histórico e analise novamente.")
            } finally { _state.update { it.copy(busy=false,deleting=false) }; refresh() }
        }
    }
    fun inspect(uri: Uri) {
        if(state.value.busy) return
        _state.update { it.copy(busy=true,fileAudit=null) }
        operation = viewModelScope.launch(Dispatchers.IO) {
            try {
                progress(0,"Calculando SHA-256 do arquivo selecionado")
                var last = 0L
                val audit = repo.security.inspect(uri) { n ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if(now-last>150) { progress(0,"SHA-256 • $n bytes lidos"); last=now }
                }
                _state.update { it.copy(fileAudit=audit) }
                repo.local.add(History(System.currentTimeMillis(),"Arquivo analisado",audit.name,"SHA-256: ${audit.hash}\n${audit.details}"))
            } catch(e: CancellationException) { message("Inspeção cancelada.") }
            catch(e: Exception) { message("Arquivo inacessível: ${e.message}") }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
    fun preference(p: Preferences) { viewModelScope.launch { try { repo.local.preferences(p) } catch(e: Exception) { message("Não foi possível salvar preferências") } } }
    fun actionOpened(name: String) {
        pendingAction = name to state.value.device
        viewModelScope.launch { try { repo.local.add(History(System.currentTimeMillis(),"Ação",name,"Tela oficial aberta. Nenhuma configuração foi alterada automaticamente.")) } catch(e: Exception) { message("Tela aberta, mas o histórico não pôde ser salvo.") } }
    }
    fun returnedFromSettings() {
        val pending = pendingAction ?: return
        pendingAction = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val after = repo.device.snapshot(); val before = pending.second
                _state.update { it.copy(device=after,performance=Analysis.performance(after)) }
                repo.local.add(History(System.currentTimeMillis(),"Observação após ação",pending.first,
                    "Espaço livre: ${before?.freeStorage} → ${after.freeStorage} bytes. RAM disponível: ${before?.freeRam} → ${after.freeRam} bytes. Economia: ${before?.powerSave} → ${after.powerSave}. Variações naturais podem ocorrer; não é prova de melhoria causada pelo app."))
            } catch(e: Exception) { message("Leitura após retorno indisponível") }
        }
    }
    fun clearLocal() {
        if(state.value.busy) return
        _state.update { it.copy(busy=true,progress="Limpando dados locais",total=null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.storage.roots().forEach { repo.storage.revoke(it) }; repo.local.clear()
                getApplication<Application>().cacheDir.listFiles()?.filter { it.name.startsWith("audit-") }?.forEach { it.delete() }
                _state.value = UiState(); refresh()
            } catch(e: Exception) { message("Limpeza local incompleta: ${e.message}"); refreshRoots() }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
    fun message(text: String?) { _state.update { it.copy(message=text) } }
}
