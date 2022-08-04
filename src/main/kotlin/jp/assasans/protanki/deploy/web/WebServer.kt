package jp.assasans.protanki.deploy.web

import com.squareup.moshi.Moshi
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import jp.assasans.protanki.deploy.IResourceManager
import jp.assasans.protanki.deploy.web.IServerClient
import jp.assasans.protanki.deploy.web.Message
import jp.assasans.protanki.deploy.web.WebSocketClient
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

interface IWebServer {
  val engine: ApplicationEngine

  val clients: MutableList<IServerClient>
  val clientFlow: SharedFlow<IServerClient>

  suspend fun run()
  suspend fun stop()
}

class WebServer(val port: Int) : IWebServer, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val resourceManager: IResourceManager by inject()
  private val json: Moshi by inject()

  override val clients: MutableList<IServerClient> = mutableListOf()

  private val _clientFlow = MutableSharedFlow<IServerClient>()
  override val clientFlow = _clientFlow.asSharedFlow()

  override lateinit var engine: ApplicationEngine

  override suspend fun run() {
    engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
      install(WebSockets)

      routing {
        static("/") {
          staticRootFolder = resourceManager.get("static").toFile()

          files(".")
          default("index.html")
        }

        webSocket("/api/ws") {
          val client = WebSocketClient(this)
          clients.add(client)
          _clientFlow.emit(client)

          for(frame in incoming) {
            if(frame !is Frame.Text) continue

            val content = frame.readText()
            try {
              val message = json.adapter(Message::class.java).fromJson(content)
              if(message == null) {
                logger.warn { "[WS] Invalid message: $content" }
                continue
              }

              // logger.debug { "[WS] Received: $message" }
              client.emit(message)
            } catch(exception: Exception) {
              logger.error(exception) { "[WS] Error on message: $content" }
            }
          }
        }
      }
    }.start()

    logger.info { "Started web server" }
  }

  override suspend fun stop() {
    logger.debug { "Stopping Ktor engine..." }
    engine.stop(2000, 3000)

    logger.info { "Stopped web server" }
  }
}
