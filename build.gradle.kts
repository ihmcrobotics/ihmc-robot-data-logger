import us.ihmc.jros2.generator.jros2GenTask

plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.log-tools-plugin") version "0.6.4"
   id("us.ihmc.jros2.generator") version "1.5.1"
}

ihmc {
   group = "us.ihmc"
   version = "0.39.4"
   vcsUrl = "https://github.com/ihmcrobotics/ihmc-robot-data-logger"
   openSource = true

   configureDependencyResolution()
   javaDirectory("main", "java-generated")
   configurePublications()
}

mainDependencies {
   api("com.google.protobuf:protobuf-java:2.6.1")
   api("net.sf.trove4j:trove4j:3.0.3")
   api("org.jcommander:jcommander:3.0")
   api("com.google.guava:guava:18.0")
   api("org.xerial.snappy:snappy-java:1.1.10.8")
   api("at.yawk.lz4:lz4-java:1.11.1")
   api("com.github.luben:zstd-jni:1.5.6-3")
   api("io.netty:netty-all:4.1.77.Final")
   api("org.openjdk.jol:jol-core:0.9")
   api("org.apache.commons:commons-text:1.9")

   api("us.ihmc:euclid:0.22.5")
   api("us.ihmc:ihmc-video-codecs:2.1.6")
   api("us.ihmc:ihmc-realtime:1.7.1")
   api("us.ihmc:ihmc-java-decklink-capture:0.4.0")
   api("us.ihmc:jros2:1.5.1")
   api("us.ihmc:ihmc-commons:0.35.1")
   api("us.ihmc:ihmc-yovariables:0.13.7")
   api("us.ihmc:scs2-definition:17-0.32.0")
   api("us.ihmc:mecano:17-0.19.3")

   api("com.fasterxml.jackson.core:jackson-databind:2.18.1")
   api("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.1")
   api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.1")
   api("com.fasterxml.jackson.dataformat:jackson-dataformat-properties:2.18.1")

   api("com.hierynomus:sshj:0.31.0")

   val openblasVersion = "0.3.28-1.5.11"
   api("org.bytedeco:openblas:$openblasVersion")
   api("org.bytedeco:openblas:$openblasVersion:linux-x86_64")
   api("org.bytedeco:openblas:$openblasVersion:linux-arm64")
   api("org.bytedeco:openblas:$openblasVersion:windows-x86_64")
   val opencvVersion = "4.10.0-1.5.11-20260819-ihmc" // Hosted on https://robotlabfiles.ihmc.us/repository
   api("us.ihmc:opencv:$opencvVersion")
   api("us.ihmc:opencv:$opencvVersion:linux-arm64")
   api("us.ihmc:opencv:$opencvVersion:linux-arm64-gpu") // Pretty much NVIDIA Orin specific
   api("us.ihmc:opencv:$opencvVersion:linux-x86_64")
   api("us.ihmc:opencv:$opencvVersion:linux-x86_64-gpu")
   api("us.ihmc:opencv:$opencvVersion:windows-x86_64")
   api("us.ihmc:opencv:$opencvVersion:windows-x86_64-gpu")
   val ffmpegVersion = "7.1-1.5.11"
   api("org.bytedeco:ffmpeg:$ffmpegVersion")
   api("org.bytedeco:ffmpeg:$ffmpegVersion:linux-arm64")
   api("org.bytedeco:ffmpeg:$ffmpegVersion:linux-x86_64")
   api("org.bytedeco:ffmpeg:$ffmpegVersion:macosx-arm64")
   api("org.bytedeco:ffmpeg:$ffmpegVersion:windows-x86_64")

   // ZED SDK for logging remote ZED data streams
   api("us.ihmc:zed-java-api:5.4.0")

   api("org.freedesktop.gstreamer:gst1-java-core:1.4.0")

   val javaFXVersion = "17.0.8"
   api(ihmc.javaFXModule("base", javaFXVersion))
   api(ihmc.javaFXModule("controls", javaFXVersion))
   api(ihmc.javaFXModule("graphics", javaFXVersion))
   api(ihmc.javaFXModule("fxml", javaFXVersion))
}

testDependencies {
   api("us.ihmc:ihmc-commons-testing:0.35.1")
}

app.entrypoint("IHMCLogger", "us.ihmc.robotDataLogger.logger.YoVariableLoggerDispatcher", listOf(
   "-XX:+UseZGC",
   "-XX:+AlwaysPreTouch",
   "-Xms1g",
   "-Xmx1g",
))
app.entrypoint("IHMCLoggerDebug5005", "us.ihmc.robotDataLogger.logger.YoVariableLoggerDispatcher", listOf(
   "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",
))
app.entrypoint("BlackMagicCapture", "us.ihmc.javadecklink.Capture")

tasks.register<JavaExec>("deploy") {
   dependsOn("generateMessages")
   dependsOn("distTar")
   group = "Deploy"
   description = "Deploy logger"
   classpath = sourceSets.main.get().runtimeClasspath
   mainClass.set("us.ihmc.publisher.logger.ui.LoggerDeployApplication")

   var p =   projectDir.toPath().resolve("build/distributions/" + project.name + "-" + project.version + ".tar").normalize()

   args("--logger-dist ", p.toString())
}

tasks.register<jros2GenTask>("generateMessages") {
   description = "Generate logger ROS 2 interfaces using jros2"
   group = "build"

   packagePaths = listOf(
      projectDir.resolve("logger_msgs").absolutePath,
   )

   outputDir = projectDir.resolve("src/main/java-generated").absolutePath
}
