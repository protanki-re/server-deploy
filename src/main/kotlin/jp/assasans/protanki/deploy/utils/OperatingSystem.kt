package jp.assasans.protanki.deploy.utils

enum class OperatingSystem {
  Unknown,

  Windows,
  Linux,
  MacOS;

  companion object {
    val current: OperatingSystem
      get() {
        val os = System.getProperty("os.name").lowercase()
        return when {
          os.startsWith("mac")     -> MacOS
          os.startsWith("windows") -> Windows
          os.startsWith("linux")   -> Linux
          else                     -> Unknown
        }
      }
  }
}
