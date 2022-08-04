package jp.assasans.protanki.deploy.web

import com.squareup.moshi.Moshi
import jp.assasans.protanki.deploy.web.IWebServer
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class WebSocketServer : ILogServer, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val server: IWebServer by inject()
  private val json: Moshi by inject()

  private val messages: MutableList<LogMessage> = mutableListOf()

  override suspend fun run(scope: CoroutineScope) {
    server.clientFlow.onEach { client ->
      messages.forEach { message -> client.send(message) }
    }.launchIn(scope)

    logger.info { "Log server registered" }
  }

  override suspend fun emit(message: LogMessage) {
    messages.add(message)
    server.clients.forEach { client -> client.send(message) }
  }

  override suspend fun clear(source: LogSource) {
    messages.removeAll { message -> message.source == source }
  }
}
