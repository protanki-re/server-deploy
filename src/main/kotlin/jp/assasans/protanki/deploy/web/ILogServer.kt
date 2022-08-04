package jp.assasans.protanki.deploy.web

import kotlinx.coroutines.CoroutineScope

interface ILogServer {
  suspend fun run(scope: CoroutineScope)
  suspend fun emit(message: LogMessage)
  suspend fun clear(source: LogSource)
}
