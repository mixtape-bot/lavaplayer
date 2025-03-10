package com.sedmelluq.lava.common.tools

/**
 * Decoded serialized exception. The original exception class is not restored, instead all exceptions will be instances
 * of this class and contain the original class name and message as fields and as the message.
 *
 * @param className       Original exception class name
 * @param originalMessage Original exception message
 * @param cause           Cause of this exception
 */
public class DecodedException(
    /**
     * Original exception class name
     */
    @JvmField public val className: String?,
    /**
     * Original exception message
     */
    @JvmField public val originalMessage: String?,
    cause: DecodedException?
) : Exception("$className: $originalMessage", cause, true, true)
