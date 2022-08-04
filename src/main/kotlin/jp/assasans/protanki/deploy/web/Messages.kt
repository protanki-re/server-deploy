package jp.assasans.protanki.deploy.web

import com.squareup.moshi.Json

abstract class Message {
  override fun toString() = "${this::class.simpleName}"
}

enum class ServerState {
  Stopped,
  Stopping,
  ProcessStarted,
  Starting,
  Started
}

class ServerStateMessage(
  @Json val state: ServerState
) : Message()

enum class ServerAction {
  Start,
  Stop
}

class ServerActionMessage(
  @Json val action: ServerAction
) : Message()

class LogMessage(
  @Json val source: LogSource,
  @Json val content: String
) : Message()

class ClearLogsMessage(
  @Json val source: LogSource
) : Message()

class VcsUpdateMessage : Message()

enum class VcsState {
  Idle,
  Updating
}

class VcsStateMessage(
  @Json val state: VcsState
) : Message()

class GradleBuildMessage : Message()

enum class GradleState {
  Idle,
  Building
}

class GradleStateMessage(
  @Json val state: GradleState
) : Message()
