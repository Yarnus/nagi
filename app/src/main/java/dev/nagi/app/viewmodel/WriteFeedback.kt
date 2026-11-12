package dev.nagi.app.viewmodel

import dev.nagi.app.BuildConfig
import dev.nagi.sdk.WriteDiagnostic
import dev.nagi.sdk.WriteResult

internal fun writeFeedback(message: String, result: WriteResult): String {
    if (!BuildConfig.DEBUG) return message
    val diagnostic = result.diagnostic ?: return message
    return "$message\n${diagnosticText(diagnostic)}"
}

internal fun diagnosticText(value: WriteDiagnostic): String = buildString {
    append("诊断 HTTP=${value.httpStatus ?: "-"} ${value.failure} ${value.format}")
    append("\ndata=${value.data} [${value.data0}/${value.data1}/${value.data2}]")
    append(" Δ=${value.delta ?: "-"} flags=${value.flags ?: "-"}")
    append("\nerror=${value.error} [${value.error0}/${value.error1}/${value.error2}] code=${value.errorCode ?: "-"}")
    append("\n__MESSAGE=${value.message} [${value.message0}/${value.message1}/${value.message6}] status=${value.messageStatus ?: "-"}")
}
