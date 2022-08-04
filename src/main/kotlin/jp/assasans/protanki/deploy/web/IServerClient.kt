package jp.assasans.protanki.deploy.web

import kotlinx.coroutines.flow.SharedFlow

interface IServerClient {
  val events: SharedFlow<Message>

  suspend fun send(message: Message)
  suspend fun close()
}
