package ru.ama.whereme.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Accepted edits finish in application scope when the settings screen closes. */
internal class SettingsWriteQueue(scope: CoroutineScope, private val onFailure: () -> Unit) {
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    internal val completion = scope.launch {
        for (command in commands) {
            try { command()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { onFailure() }
        }
    }
    fun submit(command: suspend () -> Unit) { check(commands.trySend(command).isSuccess) }
    fun close() { commands.close() }
}
