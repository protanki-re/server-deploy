package jp.assasans.protanki.deploy.ipc

import kotlinx.coroutines.flow.SharedFlow

interface IProcessNetworkingClient {
  val events: SharedFlow<ProcessMessage>

  suspend fun send(message: ProcessMessage)
  suspend fun close()
}

suspend fun ProcessMessage.send(client: IProcessNetworkingClient) {
  client.send(this)
}
