package jp.assasans.protanki.deploy.ipc

import com.squareup.moshi.Moshi
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import jp.assasans.protanki.deploy.web.IWebServer
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebSocketProcessNetworking(val url: String) : IProcessNetworking, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val server: IWebServer by inject()
  private val json: Moshi by inject()

  override val clients: MutableList<IProcessNetworkingClient> = mutableListOf()

  private val _clientFlow = MutableSharedFlow<IProcessNetworkingClient>()
  override val clientFlow = _clientFlow.asSharedFlow()

  override suspend fun run() {
    server.engine.application.routing {
      webSocket(url) {
        val client = WebSocketProcessNetworkingClient(this)
        clients.add(client)
        _clientFlow.emit(client)

        for(frame in incoming) {
          if(frame !is Frame.Text) continue

          val content = frame.readText()
          try {
            val message = json.adapter(ProcessMessage::class.java).fromJson(content)
            if(message == null) {
              logger.warn { "[IPC] Invalid message: $content" }
              continue
            }

            // logger.debug { "[IPC] Received: $message" }
            client.emit(message)
          } catch(exception: Exception) {
            logger.error(exception) { "[IPC] Error on message: $content" }
          }
        }
      }
    }

    logger.info { "IPC server registered" }
  }
}
