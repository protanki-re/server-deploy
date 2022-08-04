package jp.assasans.protanki.deploy

import jp.assasans.protanki.deploy.utils.OperatingSystem
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface IServerBuilder {
  suspend fun build(root: Path): Process
}

class ServerBuilder : IServerBuilder {
  override suspend fun build(root: Path): Process {
    /*
    val shell = when(OperatingSystem.current) {
      OperatingSystem.Windows -> "powershell.exe"
      OperatingSystem.Linux   -> "/bin/bash"
      OperatingSystem.MacOS   -> "/bin/bash"
      else                    -> throw IllegalStateException("Unsupported OS: ${OperatingSystem.current}")
    }

    val builder = ProcessBuilder()
      .command(
        shell,
        root.resolve("gradlew").absolute().pathString
      )
      .directory(root.absolute().toFile())
    val process = withContext(Dispatchers.IO) { builder.start() }
    */

    val file = when(OperatingSystem.current) {
      OperatingSystem.Windows -> "gradlew.bat"
      OperatingSystem.Linux   -> "gradlew"
      OperatingSystem.MacOS   -> "gradlew"
      else                    -> throw IllegalStateException("Unsupported OS: ${OperatingSystem.current}")
    }
    val process = withContext(Dispatchers.IO) {
      ProcessBuilder()
        .command(root.resolve(file).absolute().pathString, "build", "--console=plain")
        .directory(root.toFile())
        .start()
    }
    return process
  }
}
