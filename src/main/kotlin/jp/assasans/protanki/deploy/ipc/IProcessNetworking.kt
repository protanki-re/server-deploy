package jp.assasans.protanki.deploy.ipc

import kotlinx.coroutines.flow.SharedFlow

interface IProcessNetworking {
  val clients: MutableList<IProcessNetworkingClient>
  val clientFlow: SharedFlow<IProcessNetworkingClient>

  suspend fun run()
}
