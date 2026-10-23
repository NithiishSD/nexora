package com.nexora.desktop.ui.components

import com.nexora.desktop.api.ApiError
import com.nexora.desktop.util.Strings
import com.nexora.desktop.util.s

/**
 * Turns a stable error `code` into the sentence the owner reads (06 section 9). The screens switch on the code,
 * never on the server's `detail` text, which may be reworded; the detail is shown underneath in small print.
 */
object ErrorMessages {

    fun message(code: String): String = Strings.getOrNull("error.$code") ?: s("error.unknown", code)

    fun message(error: ApiError): String = message(error.code)

    /** The API detail, or nothing for network failures (the message already says everything). */
    fun detail(error: ApiError): String? = if (error.isNetwork) null else error.detail
}
