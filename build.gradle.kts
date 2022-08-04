import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  id("com.github.gmazzo.buildconfig") version "3.0.3"
  id("com.github.johnrengelman.shadow") version "7.1.2"
  distribution
  application
}

group = "jp.assasans.protanki.deploy"
version = "0.1.0"

repositories {
  mavenCentral()
  maven {
    url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
    name = "ktor-eap"
  }
}

dependencies {
  implementation(kotlin("stdlib"))

  implementation("io.ktor:ktor-server-core:2.0.0-beta-1")
  implementation("io.ktor:ktor-network:2.0.0-beta-1")
  implementation("io.ktor:ktor-server-netty:2.0.0-beta-1")
  implementation("io.ktor:ktor-server-websockets:2.0.0-beta-1")
  implementation("io.ktor:ktor-client-core:2.0.0-beta-1")
  implementation("io.ktor:ktor-client-cio:2.0.0-beta-1")

  implementation("org.eclipse.jgit:org.eclipse.jgit:6.2.0.202206071550-r")

  implementation("com.squareup.moshi:moshi:1.13.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.13.0")
  implementation("com.squareup.moshi:moshi-adapters:1.13.0")

  implementation("io.insert-koin:koin-core:3.1.5")
  implementation("io.insert-koin:koin-logger-slf4j:3.1.5")

  implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")

  implementation("org.reflections:reflections:0.10.2")

  implementation("ch.qos.logback:logback-classic:1.2.11")
  implementation("io.github.microutils:kotlin-logging:2.1.21")

  implementation("com.github.ajalt.clikt:clikt:3.5.0")
}

tasks.withType<KotlinCompile> {
  kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.ExperimentalStdlibApi"
  kotlinOptions.jvmTarget = "17"
}

sourceSets {
  main {
    resources {
      exclude("data")
    }
  }
}

buildConfig {
  useKotlinOutput()
  packageName("jp.assasans.protanki.deploy")

  buildConfigField("String", "VERSION", "\"${project.version}\"")
}

distributions {
  all {
    contents {
      from("src/main/resources/data") {
        into("data")
      }
    }
  }
}

tasks {
  wrapper {
    gradleVersion = "7.4.1"
    distributionType = Wrapper.DistributionType.BIN
  }

  jar {
    archiveBaseName.set("protanki-deploy")
    archiveVersion.set("${project.version}")

    manifest {
      attributes["Main-Class"] = application.mainClass
      attributes["Implementation-Version"] = project.version
    }

    dependsOn("copyDependencies")
    dependsOn("copyRuntimeResources")
  }

  shadowJar {
    archiveBaseName.set("protanki-deploy")
    archiveVersion.set("${project.version}")

    manifest {
      attributes["Main-Class"] = application.mainClass
      attributes["Implementation-Version"] = project.version
    }

    dependsOn("copyRuntimeResources")
  }

  register<Sync>("copyRuntimeResources") {
    // Copy runtime resources to the jar directory
    from("$projectDir/src/main/resources/data")
    into("$buildDir/data")
  }

  register<Sync>("copyDependencies") {
    from(configurations.default)
    into("$buildDir/dependencies")
  }
}

application {
  mainClass.set("jp.assasans.protanki.deploy.MainKt")
}
