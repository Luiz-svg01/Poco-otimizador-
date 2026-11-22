package br.com.pocooptimizer.domain

object Analysis {
    const val DAY = 86_400_000L
    fun categories(f: FileEntry, now: Long, large: Long = 100L * 1024 * 1024, oldDays: Int = 90): List<String> = buildList {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val old = f.modified > 0 && now - f.modified > oldDays * DAY
        if (f.size >= large) add(if (f.mime.startsWith("video/")) "Vídeo grande" else "Arquivo grande")
        if (old && f.path.split('/').any { it.equals("download", true) || it.equals("downloads", true) }) add("Download antigo")
        if (ext == "apk") add(if (old) "APK antigo" else "Pacote APK")
        if (ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2")) add("Arquivo compactado")
        if (old && (f.mime.startsWith("text/") || ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "odt"))) add("Documento antigo")
        if (ext in setOf("tmp", "temp")) add("Possível temporário — revisar")
    }
    fun performance(d: DeviceSnapshot): List<Finding> = buildList {
        if (d.totalStorage > 0 && (d.freeStorage < 5L * 1024 * 1024 * 1024 || d.freeStorage.toDouble() / d.totalStorage < .1))
            add(Finding("Pouco armazenamento disponível", "O volume de dados está com pouco espaço livre.", "Revise arquivos grandes e apps desnecessários."))
        if (d.lowMemory) add(Finding("Pressão de memória", "O Android informou lowMemory nesta leitura.", "Observe se a condição persiste; reinicie o aparelho se houver falhas. Fechar apps não garante melhora."))
        if (d.temperature != null && d.temperature >= 45) add(Finding("Bateria aquecida", "Sensor reportou ${d.temperature} °C.", "Interrompa atividades intensas, evite sol e observe o resfriamento.", Risk.POTENTIAL))
        if (d.battery != null && d.battery <= 20 && d.charging == false && !d.powerSave)
            add(Finding("Bateria baixa", "Carga de ${d.battery}% sem economia de energia.", "Considere ativar a economia de energia nas configurações."))
        if (d.batteryHealth in setOf(3, 4, 5, 6, 7)) add(Finding("Estado da bateria requer atenção", "O Android retornou código de saúde ${d.batteryHealth}.", "Consulte o diagnóstico oficial e assistência se persistir.", Risk.POTENTIAL))
    }
    fun permissionFindings(granted: Set<String>): List<Finding> = buildList {
        val sms = "android.permission.READ_SMS" in granted
        val contacts = "android.permission.READ_CONTACTS" in granted
        if (granted.any { it in sensitive })
            add(Finding("Permissões sensíveis concedidas", granted.filter { it in sensitive }.joinToString(), "Confira se são necessárias para a função do app."))
        if (sms && contacts) add(Finding("Combinação de SMS e contatos", "Ambas as permissões aparecem como concedidas.", "Revise a finalidade e a origem do app. Essa combinação não prova abuso.", Risk.POTENTIAL))
    }
    val sensitive = setOf("android.permission.READ_SMS", "android.permission.READ_CONTACTS", "android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION", "android.permission.READ_CALL_LOG")
    fun preservesCopies(selected: Set<String>, groups: List<DuplicateGroup>): Boolean =
        groups.all { g -> g.files.any { it.uri !in selected } }
}
