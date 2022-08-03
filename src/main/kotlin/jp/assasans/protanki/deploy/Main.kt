package jp.assasans.protanki.deploy

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import jp.assasans.protanki.deploy.extensions.cast
import jp.assasans.protanki.deploy.extensions.pipeTo
import jp.assasans.protanki.deploy.ipc.*
import mu.KotlinLogging
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module
import org.koin.logger.SLF4JLogger
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.reflect.KClass
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

fun main(args: Array<String>) = object : CliktCommand() {
  val serverRoot by option("--server", help = "ProTanki server build directory").required()
  val port by option("--port", help = "Web server / IPC port").int().default(5555)

  override fun run(): Unit = runBlocking {
    val logger = KotlinLogging.logger { }

    logger.info { "Hello, 世界!" }
    logger.info { "Root path: ${Paths.get("").absolute()}" }

    val ipcUrl = "/ipc/server"

    val module = module {
      single<IWebServer> { WebServer(port) }
      single<IProcessNetworking> { WebSocketProcessNetworking(ipcUrl) }
      single {
        Moshi.Builder()
          .add(
            PolymorphicJsonAdapterFactory.of(ProcessMessage::class.java, "_").let {
              var factory = it
              val reflections = Reflections("jp.assasans.protanki.deploy")

              reflections.get(Scanners.SubTypes.of(ProcessMessage::class.java).asClass<ProcessMessage>()).forEach { type ->
                val messageType = type.kotlin.cast<KClass<ProcessMessage>>()
                val name = messageType.simpleName ?: throw IllegalStateException("$messageType has no simple name")

                factory = factory.withSubtype(messageType.java, name.removeSuffix("Message"))
                logger.debug { "Registered IPC message: $name" }
              }

              factory
            }
          )
          .add(KotlinJsonAdapterFactory())
          .build()
      }
    }

    val koin = startKoin {
      logger(SLF4JLogger(Level.ERROR))

      modules(module)
    }

    // TODO(Assasans)
    koin.koin.get<IWebServer>().run()
    koin.koin.get<IProcessNetworking>().run()

    val ipc by koin.koin.inject<IProcessNetworking>()

    val root = Paths.get(serverRoot).absolute()
    logger.info { "Server root path: ${root.absolute()}" }

    val mainJar = root.resolve("libs/protanki-server-0.1.0.jar") // TODO(Assasans)
    val dependencies = root.resolve("dependencies")
      .listDirectoryEntries()
      .filter { path -> path.isRegularFile() }
      .map { path -> path.absolute().pathString }
    val classpath = dependencies + mainJar.pathString

    val command = listOf(
      "java",
      "-Dfile.encoding=UTF-8",
      "-cp",
      classpath.joinToString(File.pathSeparator),
      "jp.assasans.protanki.server.MainKt",
      "--ipc-url",
      "ws://localhost:$port$ipcUrl"
    )
    val builder = ProcessBuilder()
      .command(command)
      .directory(root.toFile())

    logger.info { "Command: ${command.joinToString(" ")}" }
    logger.info { "===== SERVER LOG =====" }
    val process = withContext(Dispatchers.IO) { builder.start() }

    Runtime.getRuntime().addShutdownHook(Thread {
      logger.info { "Stopping server..." }
      process.destroy()
    })

    coroutineScope {
      launch { process.inputStream.pipeTo(System.out) }
      launch { process.errorStream.pipeTo(System.err) }

      launch {
        logger.info { "IPC coroutine started" }

        ipc.clientFlow.collect { client ->
          logger.info { "Client connected to the IPC server" }
          client.events.collect { event ->
            // logger.debug { "[IPC] Received event: $event" }
            when(event) {
              is ServerStartingMessage -> logger.info { "[IPC] Starting server..." }
              is ServerStartedMessage  -> logger.info { "[IPC] Server started" }

              else                     -> logger.info { "[IPC] Unknown event: ${event::class.simpleName}" }
            }
          }
        }
      }
    }

    withContext(Dispatchers.IO) { process.waitFor() }
    // Semaphore(1, 1).acquire()
  }
}.main(args)
