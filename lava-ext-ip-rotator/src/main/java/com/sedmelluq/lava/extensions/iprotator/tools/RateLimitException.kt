package com.sedmelluq.lava.extensions.iprotator.tools

public class RateLimitException : RuntimeException {
    public constructor() : super()

    public constructor(message: String) : super(message)

    public constructor(message: String, cause: Throwable) : super(message, cause)
}
