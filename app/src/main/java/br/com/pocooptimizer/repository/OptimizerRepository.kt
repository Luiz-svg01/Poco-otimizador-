package br.com.pocooptimizer.repository

import android.content.Context
import br.com.pocooptimizer.data.LocalStore
import br.com.pocooptimizer.scanner.SecurityScanner
import br.com.pocooptimizer.storage.*

class OptimizerRepository(context: Context) {
    val storage = SafStorage(context)
    val files = SafeFiles(storage)
    val device = DeviceRepository(context)
    val security = SecurityScanner(context, storage)
    val local = LocalStore(context)
}
