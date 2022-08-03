package jp.assasans.protanki.deploy.ipc

abstract class ProcessMessage {
  override fun toString() = "${this::class.simpleName}"
}

class ServerStartingMessage : ProcessMessage()
class ServerStartedMessage : ProcessMessage()

class ServerStopRequest : ProcessMessage()
class ServerStopResponse : ProcessMessage()
