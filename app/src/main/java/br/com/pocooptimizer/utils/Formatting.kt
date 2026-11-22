package br.com.pocooptimizer.utils

import java.text.DateFormat
import java.util.Date
import java.util.Locale
fun bytes(n: Long): String {
    if(n < 0) return "Tamanho desconhecido"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value=n.toDouble(); var i=0
    while(value>=1024 && i<units.lastIndex) { value/=1024; i++ }
    return String.format(Locale.getDefault(), "%.1f %s",value,units[i])
}
fun date(n: Long): String = if(n <= 0) "Não disponível" else DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(n))
