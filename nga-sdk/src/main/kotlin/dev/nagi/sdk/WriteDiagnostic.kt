package dev.nagi.sdk

data class WriteDiagnostic(
    val httpStatus: Int? = null,
    val format: WriteResponseFormat = WriteResponseFormat.UNAVAILABLE,
    val data: WriteFieldShape = WriteFieldShape.MISSING,
    val data0: WriteFieldShape = WriteFieldShape.MISSING,
    val data1: WriteFieldShape = WriteFieldShape.MISSING,
    val data2: WriteFieldShape = WriteFieldShape.MISSING,
    val error: WriteFieldShape = WriteFieldShape.MISSING,
    val error0: WriteFieldShape = WriteFieldShape.MISSING,
    val error1: WriteFieldShape = WriteFieldShape.MISSING,
    val error2: WriteFieldShape = WriteFieldShape.MISSING,
    val message: WriteFieldShape = WriteFieldShape.MISSING,
    val message0: WriteFieldShape = WriteFieldShape.MISSING,
    val message1: WriteFieldShape = WriteFieldShape.MISSING,
    val message6: WriteFieldShape = WriteFieldShape.MISSING,
    val messageStatus: Int? = null,
    val delta: Int? = null,
    val flags: Int? = null,
    val errorCode: Int? = null,
    val failure: WriteFailure = WriteFailure.UNCONFIRMED,
)

enum class WriteResponseFormat { JSON, XML, HTML, TEXT, EMPTY, UNAVAILABLE }
enum class WriteFieldShape { MISSING, NULL, OBJECT, ARRAY, STRING, NUMBER, BOOLEAN }
enum class WriteFailure { UNCONFIRMED, NETWORK, HTTP, AUTHENTICATION, RESTRICTED, BUSINESS, PROTOCOL }
