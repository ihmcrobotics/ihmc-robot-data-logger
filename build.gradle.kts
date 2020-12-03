import us.ihmc.idl.generator.IDLGenerator

buildscript {
   dependencies {
      classpath("us.ihmc:ihmc-pub-sub-generator:0.15.0")
   }
}

plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.4"
   id("us.ihmc.ihmc-cd") version "1.17"
   id("us.ihmc.log-tools-plugin") version "0.5.0"
}

ihmc {
   group = "us.ihmc"
   version = "0.20.3"
   vcsUrl = "https://github.com/ihmcrobotics/ihmc-robot-data-logger"
   openSource = true

   configureDependencyResolution()
   resourceDirectory("main", "idl")
   javaDirectory("main", "java-generated")
   configurePublications()
}

mainDependencies {
   api("com.google.protobuf:protobuf-java:2.6.1")
   api("net.sf.trove4j:trove4j:3.0.3")
   api("com.martiansoftware:jsap:2.1")
   api("org.apache.commons:commons-lang3:3.11")
   api("com.google.guava:guava:18.0")
   api("commons-io:commons-io:2.8.0")
   api("org.tukaani:xz:1.5")
   api("org.xerial.snappy:snappy-java:1.1.1-M1")
   api("net.jpountz.lz4:lz4:1.3.0")
   api("io.netty:netty-all:4.1.32.Final")
   api("org.openjdk.jol:jol-core:0.9")

   api("us.ihmc:euclid:0.15.2")
   api("us.ihmc:ihmc-yovariables:0.9.7")
   api("us.ihmc:ihmc-video-codecs:2.1.6")
   api("us.ihmc:ihmc-realtime:1.3.1")
   api("us.ihmc:ihmc-java-decklink-capture:0.3.4")
   api("us.ihmc:ihmc-pub-sub:0.15.0")
   api("us.ihmc:ihmc-pub-sub-serializers-extra:0.15.0")
   api("us.ihmc:ihmc-commons:0.30.4")
   api("us.ihmc:ihmc-graphics-description:0.19.2")
   api("us.ihmc:mecano:0.7.2")
}

testDependencies {
   api("us.ihmc:ihmc-commons-testing:0.30.4")
}

app.entrypoint("IHMCLogger", "us.ihmc.robotDataLogger.logger.YoVariableLoggerDispatcher")

tasks.create("deploy") {
   dependsOn("installDist")

   doLast {
      generateMessages()
      deployLogger()
   }
}

fun generateMessages()
{
   val idlFiles = fileTree("src/main/idl")
   val targetDirectory = file("src/main/java-generated")
   val packagePrefix = ""

   for (idl in idlFiles)
   {
      IDLGenerator.execute(idl, packagePrefix, targetDirectory, listOf(file(".")))
   }
}

val loggerDirectory = "IHMCLogger"
val loggerHostname: String by project
val loggerUsername: String by project
val loggerPassword: String by project
val distFolder by lazy { tasks.named<Sync>("installDist").get().destinationDir.toString() }

fun deployLogger()
{
   if (project.hasProperty("loggerPassword"))
   {
      remote.session(loggerHostname, loggerUsername, loggerPassword)
      {
         deployFunction()
      }
   }
   else
   {
      remote.session(loggerHostname, loggerUsername)
      {
         deployFunction()
      }
   }
}

fun us.ihmc.cd.RemoteExtension.RemoteConnection.deployFunction()
{
   exec("mkdir -p ~/$loggerDirectory")

   exec("rm -rf ~/$loggerDirectory/bin")
   exec("rm -rf ~/$loggerDirectory/lib")

   put(file("$distFolder/bin").toString(), "$loggerDirectory/bin")
   put(file("$distFolder/lib").toString(), "$loggerDirectory/lib")

   exec("chmod +x ~/$loggerDirectory/bin/IHMCLogger")
}
