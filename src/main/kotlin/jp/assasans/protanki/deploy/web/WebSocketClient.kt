package jp.assasans.protanki.deploy.web

import io.ktor.websocket.*
import jp.assasans.protanki.deploy.toJson
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketClient(
  private val session: WebSocketSession
) : IServerClient, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val _events = MutableSharedFlow<Message>()
  override val events = _events.asSharedFlow()

  override suspend fun send(message: Message) {
    session.send(message.toJson())
    // logger.debug { "[WS] Sent: $message" }
  }

  override suspend fun close() {
    session.close()
  }

  suspend fun emit(message: Message) {
    _events.emit(message)
  }
}
