package com.sedmelluq.lava.common.tools.extensions

import com.sedmelluq.lava.common.tools.DecodedException
import com.sedmelluq.lava.common.tools.exception.FriendlyException
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * Writes a string to output with the additional information whether it is `null` or not. Compatible with
 * [DataInput.readNullableUTF].
 *
 * @param text   Text to write.
 *
 * @throws IOException On write error.
 */
@Throws(IOException::class)
public fun DataOutput.writeNullableUTF(text: String?) {
    writeBoolean(text != null)
    if (text != null) {
        writeUTF(text)
    }
}

/**
 * Reads a string from input which may be `null`. Compatible with
 * [DataOutput.writeNullableUTF].
 *
 * @return The string that was read, or `null`.
 * @throws IOException On read error.
 */
@Throws(IOException::class)
public fun DataInput.readNullableUTF(): String? = if (readBoolean()) readUTF() else null

/**
 * Decode an exception from an input stream
 *
 * @return Decoded exception
 * @throws IOException On IO error
 */
@Throws(IOException::class)
public fun DataInput.readException(): FriendlyException {
    var cause: DecodedException? = null
    while (readBoolean()) {
        cause = DecodedException(readNullableUTF(), readNullableUTF(), cause)
        cause.stackTrace = readStackTrace()
    }

    val exception = FriendlyException(
        readNullableUTF(),
        FriendlyException.Severity[readInt()],
        cause
    )

    exception.stackTrace = readStackTrace()
    return exception
}

@Throws(IOException::class)
private fun DataInput.readStackTrace(): Array<StackTraceElement> = Array(readInt()) { readStackTraceElement() }

private fun DataInput.readStackTraceElement(): StackTraceElement =
    StackTraceElement(readUTF(), readUTF(), readNullableUTF(), readInt())

/**
 * Encode an exception to an output stream
 *
 * @param exception Exception to encode
 * @throws IOException On IO error
 */
@Throws(IOException::class)
public fun DataOutput.writeException(exception: FriendlyException) {
    val causes = mutableListOf<Throwable>()

    var next = exception.cause

    while (next != null) {
        causes.add(next)
        next = next.cause
    }

    for (cause in causes.reversed()) {
        writeBoolean(true)

        val message: String? = if (cause is DecodedException) {
            writeNullableUTF(cause.className)
            cause.originalMessage
        } else {
            writeNullableUTF(cause.javaClass.name)
            cause.message
        }

        writeNullableUTF(message)
        writeStackTrace(cause)
    }

    writeBoolean(false)
    writeNullableUTF(exception.message)
    writeInt(exception.severity.ordinal)

    writeStackTrace(exception)
}

@Throws(IOException::class)
private fun DataOutput.writeStackTrace(throwable: Throwable) {
    val trace = throwable.stackTrace
    writeInt(trace.size)

    for (element in trace) {
        encodeStackTraceElement(element)
    }
}

private fun DataOutput.encodeStackTraceElement(stackTraceElement: StackTraceElement) {
    writeUTF(stackTraceElement.className)
    writeUTF(stackTraceElement.methodName)
    writeNullableUTF(stackTraceElement.fileName)
    writeInt(stackTraceElement.lineNumber)
}
