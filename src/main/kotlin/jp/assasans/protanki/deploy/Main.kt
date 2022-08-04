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
import jp.assasans.protanki.deploy.ipc.*
import jp.assasans.protanki.deploy.web.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun main(args: Array<String>) = object : CliktCommand() {
  val serverRoot by option("--server", help = "ProTanki server build directory").required()
  val port by option("--port", help = "Web server / IPC port").int().default(5555)

  override fun run(): Unit = runBlocking {
    val logger = KotlinLogging.logger { }

    logger.info { "Hello, 世界!" }
    logger.info { "Root path: ${Paths.get("").absolute()}" }

    val ipcUrl = "/ipc/server"

    val reflections = Reflections("jp.assasans.protanki.deploy")

    val module = module {
      single<IWebServer> { WebServer(port) }
      single<IProcessNetworking> { WebSocketProcessNetworking(ipcUrl) }
      single<IServerBuilder> { ServerBuilder() }
      single<ILogServer> { WebSocketServer() }
      single<IResourceManager> { ResourceManager() }
      single {
        Moshi.Builder()
          .add(
            PolymorphicJsonAdapterFactory.of(ProcessMessage::class.java, "_").let {
              var factory = it

              reflections.get(Scanners.SubTypes.of(ProcessMessage::class.java).asClass<ProcessMessage>()).forEach { type ->
                val messageType = type.kotlin.cast<KClass<ProcessMessage>>()
                val name = messageType.simpleName ?: throw IllegalStateException("$messageType has no simple name")

                factory = factory.withSubtype(messageType.java, name.removeSuffix("Message"))
                logger.debug { "Registered IPC message: $name" }
              }

              factory
            }
          )
          .add(
            PolymorphicJsonAdapterFactory.of(Message::class.java, "_").let {
              var factory = it

              reflections.get(Scanners.SubTypes.of(Message::class.java).asClass<Message>()).forEach { type ->
                val messageType = type.kotlin.cast<KClass<Message>>()
                val name = messageType.simpleName ?: throw IllegalStateException("$messageType has no simple name")

                factory = factory.withSubtype(messageType.java, name.removeSuffix("Message"))
                logger.debug { "Registered WebSocket message: $name" }
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
    koin.koin.get<ILogServer>().run(CoroutineScope(coroutineContext)) // TODO(Assasans)

    val ipc by koin.koin.inject<IProcessNetworking>()
    val logServer by koin.koin.inject<ILogServer>()
    val webServer by koin.koin.inject<IWebServer>()
    val serverBuilder by koin.koin.inject<IServerBuilder>()

    val root = Paths.get(serverRoot).absolute()
    logger.info { "Server root path: ${root.absolute()}" }

    val vcs: IVersionControl = GitVersionControl(
      root.resolve("../.git").absolute(),
      remote = "origin",
      branch = "main"
    )

    // serverBuilder.build(root.resolve("..")).let { process ->
    //   coroutineScope {
    //     launch { process.inputStream.pipeTo(System.out) }
    //     launch { process.errorStream.pipeTo(System.err) }
    //   }
    // }
    //
    // return@runBlocking

    val state: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Stopped)
    val vcsState: MutableStateFlow<VcsState> = MutableStateFlow(VcsState.Idle)
    val gradleState: MutableStateFlow<GradleState> = MutableStateFlow(GradleState.Idle)

    var process: Process? = null
    var gradleProcess: Process? = null

    Runtime.getRuntime().addShutdownHook(Thread {
      process?.let { process ->
        if(!process.isAlive) return@let

        logger.info { "Stopping server..." }
        process.destroy()
      }

      gradleProcess?.let { process ->
        if(!process.isAlive) return@let

        logger.info { "Stopping build..." }
        process.destroy()
      }
    })

    val processScope = CoroutineScope(coroutineContext)
    val gradleScope = CoroutineScope(coroutineContext)

    suspend fun startServer() {
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

      val newProcess = withContext(Dispatchers.IO) { builder.start() }
      process = newProcess
      state.emit(ServerState.ProcessStarted)

      with(processScope) {
        // launch { newProcess.inputStream.pipeTo(System.out) }
        // launch { newProcess.errorStream.pipeTo(System.err) }

        launch {
          val reader = newProcess.inputStream.reader()
          val writer = System.out.writer()
          val buffer = CharArray(1024)
          withContext(Dispatchers.IO) {
            while(true) {
              val count = reader.read(buffer)
              if(count == -1) break

              writer.write(buffer, 0, count)
              writer.flush()
              logServer.emit(LogMessage(source = LogSource.Server, content = String(buffer, 0, count)))
            }
          }
        }

        launch {
          val reader = newProcess.errorStream.reader()
          val writer = System.err.writer()
          val buffer = CharArray(1024)
          withContext(Dispatchers.IO) {
            while(true) {
              val count = reader.read(buffer)
              if(count == -1) break

              writer.write(buffer, 0, count)
              writer.flush()
              logServer.emit(LogMessage(source = LogSource.Server, content = String(buffer, 0, count)))
            }
          }
        }

        launch {
          withContext(Dispatchers.IO) { newProcess.waitFor() }
          logger.info { "===== SERVER STOPPED =====" }

          state.emit(ServerState.Stopped)

          // koin.koin.get<IWebServer>().stop()

          // TODO(Assasans)
          // GlobalScope.launch { this@coroutineScope.cancel() }
        }
      }
    }

    suspend fun buildServer() {
      logger.info { "===== BUILD LOG =====" }
      val newProcess = serverBuilder.build(root.resolve("../").absolute())
      gradleProcess = newProcess

      with(gradleScope) {
        // launch { newProcess.inputStream.pipeTo(System.out) }
        // launch { newProcess.errorStream.pipeTo(System.err) }

        launch {
          val reader = newProcess.inputStream.reader()
          val writer = System.out.writer()
          val buffer = CharArray(1024)
          withContext(Dispatchers.IO) {
            while(true) {
              val count = reader.read(buffer)
              if(count == -1) break

              writer.write(buffer, 0, count)
              writer.flush()
              logServer.emit(LogMessage(source = LogSource.Gradle, content = String(buffer, 0, count)))
            }
          }
        }

        launch {
          val reader = newProcess.errorStream.reader()
          val writer = System.err.writer()
          val buffer = CharArray(1024)
          withContext(Dispatchers.IO) {
            while(true) {
              val count = reader.read(buffer)
              if(count == -1) break

              writer.write(buffer, 0, count)
              writer.flush()
              logServer.emit(LogMessage(source = LogSource.Gradle, content = String(buffer, 0, count)))
            }
          }
        }

        launch {
          withContext(Dispatchers.IO) { newProcess.waitFor() }
          logger.info { "===== BUILD FINISHED =====" }

          logger.info { "Gradle build: finished" }
          logServer.emit(LogMessage(source = LogSource.Gradle, content = "Build: finished\n"))

          gradleState.emit(GradleState.Idle)
        }
      }
    }

    try {
      coroutineScope {
        launch {
          // New clients
          webServer.clientFlow.collect { client ->
            client.send(ServerStateMessage(state = state.value))
            client.send(VcsStateMessage(state = vcsState.value))
            client.send(GradleStateMessage(state = gradleState.value))

            client.events.onEach { message ->
              when(message) {
                is ServerActionMessage -> {
                  when(message.action) {
                    ServerAction.Start -> startServer()
                    ServerAction.Stop  -> {
                      state.emit(ServerState.Stopping)
                      ServerStopRequest().send(ipc.clients.single())
                    }
                  }
                }
                is ClearLogsMessage    -> {
                  val source = message.source
                  logServer.clear(source)

                  logger.debug { "Cleared logs for $source" }
                }

                is VcsUpdateMessage    -> {
                  vcsState.emit(VcsState.Updating)

                  logger.info { "VCS update requested" }
                  logServer.emit(LogMessage(source = LogSource.Git, content = "Update requested\n"))
                  logServer.emit(LogMessage(source = LogSource.Git, content = "Remote: ${vcs.remote}, branch: ${vcs.branch}\n"))

                  val currentCommit = vcs.getCurrentCommit()
                  val remoteCommit = vcs.getLastRemoteCommit()

                  logServer.emit(
                    LogMessage(
                      source = LogSource.Git,
                      content = "Current commit: ${currentCommit.name.take(8)} - ${currentCommit.shortMessage}\n"
                    )
                  )
                  if(currentCommit.name == remoteCommit.name) {
                    logger.info { "VCS update: ${currentCommit.name.take(8)}, up-to-date" }
                    logServer.emit(LogMessage(source = LogSource.Git, content = "Update: ${currentCommit.name.take(8)}, up-to-date\n"))
                  } else {
                    logServer.emit(
                      LogMessage(
                        source = LogSource.Git, content = "Remote commit: ${remoteCommit.name.take(8)} - ${remoteCommit.shortMessage}\n"
                      )
                    )

                    logger.info { "VCS update: ${currentCommit.name.take(8)}..${remoteCommit.name.take(8)}" }
                    logServer.emit(
                      LogMessage(
                        source = LogSource.Git,
                        content = "Updating: ${currentCommit.name.take(8)}..${remoteCommit.name.take(8)}\n"
                      )
                    )

                    vcs.checkout(remoteCommit)

                    logger.info { "VCS update: checked out" }
                    logServer.emit(LogMessage(source = LogSource.Git, content = "Update: checked out\n"))
                  }

                  vcsState.emit(VcsState.Idle)
                }

                is GradleBuildMessage  -> {
                  gradleState.emit(GradleState.Building)

                  logger.info { "Gradle build: started" }
                  logServer.emit(LogMessage(source = LogSource.Gradle, content = "Build: started\n"))

                  buildServer()
                }
              }
            }.launchIn(this)
          }
        }

        launch {
          // Server state update
          state.collect { state ->
            webServer.clients.forEach { client ->
              client.send(ServerStateMessage(state = state))
            }
          }
        }

        launch {
          // VCS state update
          vcsState.collect { state ->
            webServer.clients.forEach { client ->
              client.send(VcsStateMessage(state = state))
            }
          }
        }

        launch {
          // Gradle state update
          gradleState.collect { state ->
            webServer.clients.forEach { client ->
              client.send(GradleStateMessage(state = state))
            }
          }
        }

        launch {
          logger.info { "IPC coroutine started" }

          ipc.clientFlow.collect { client ->
            logger.info { "Client connected to the IPC server" }
            client.events.onEach { event ->
              // logger.debug { "[IPC] Received event: $event" }
              when(event) {
                is ServerStartingMessage -> {
                  logger.info { "[IPC] Starting server..." }
                  state.emit(ServerState.Starting)
                }
                is ServerStartedMessage  -> {
                  logger.info { "[IPC] Server started" }
                  state.emit(ServerState.Started)
                }
                is ServerStopResponse    -> {
                  logger.info { "[IPC] Server stopped" }
                  state.emit(ServerState.Stopped)
                }

                else                     -> logger.info { "[IPC] Unknown event: ${event::class.simpleName}" }
              }
            }.launchIn(this)
          }
        }
      }
    } catch(exception: CancellationException) {
      logger.debug { "Coroutune scope cancelled" }
    }

    // Semaphore(1, 1).acquire()
  }
}.main(args)
