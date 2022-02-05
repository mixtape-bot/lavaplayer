package com.sedmelluq.discord.lavaplayer.source.common

import java.util.regex.Matcher

/**
 * @param identifier The identifier that was used.
 * @param matcher The pattern matcher
 */
data class ExtractorContext(val identifier: String, val matcher: Matcher)
