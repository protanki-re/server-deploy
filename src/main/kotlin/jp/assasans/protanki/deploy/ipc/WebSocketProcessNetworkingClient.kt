package jp.assasans.protanki.deploy.ipc

import io.ktor.websocket.*
import jp.assasans.protanki.deploy.toJson
import mu.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketProcessNetworkingClient(
  private val session: WebSocketSession
) : IProcessNetworkingClient {
  private val logger = KotlinLogging.logger { }

  private val _events = MutableSharedFlow<ProcessMessage>()
  override val events = _events.asSharedFlow()

  override suspend fun send(message: ProcessMessage) {
    session.send(message.toJson())
    logger.debug { "[IPC] Sent: $message" }
  }

  override suspend fun close() {
    session.close()
  }

  suspend fun emit(message: ProcessMessage) {
    _events.emit(message)
  }
}
