package jp.assasans.protanki.deploy.ipc

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import mu.KotlinLogging
import org.koin.core.component.KoinComponent

interface IWebServer {
  val engine: ApplicationEngine

  suspend fun run()
}

class WebServer(val port: Int) : IWebServer, KoinComponent {
  private val logger = KotlinLogging.logger { }

  override lateinit var engine: ApplicationEngine

  override suspend fun run() {
    engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
      install(WebSockets)

      routing {
        get("/") {
          call.respondText(ContentType.Text.Html) {
            """
            |<!DOCTYPE html>
            |<html>
            |<head>
            |  <meta charset="utf-8">
            |  <title>ProTanki Deploy</title>
            |</head>
            |<body>
            |  <h1>TODO</h1>
            |</body>
            |</html>
            """.trimMargin()
          }
        }
      }
    }.start()

    logger.info { "Started web server" }
  }
}
