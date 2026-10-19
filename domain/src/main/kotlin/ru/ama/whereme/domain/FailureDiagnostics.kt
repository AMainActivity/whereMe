package ru.ama.whereme.domain

import java.util.logging.Logger

/** Log operation/type only. Exception messages can contain credentials, URLs or coordinates. */
fun reportFailure(operation: String, failure: Throwable) {
    Logger.getLogger("whereMe").warning("$operation: ${failure.javaClass.simpleName}; cause=${failure.cause?.javaClass?.simpleName.orEmpty()}")
}
