import us.ihmc.idl.generator.IDLGenerator

buildscript {
   dependencies {
      classpath("us.ihmc:ihmc-pub-sub-generator:0.18.5")
   }
}

plugins {
   id("us.ihmc.ihmc-build")
   id("us.ihmc.ihmc-ci") version "7.7"
   id("us.ihmc.ihmc-cd") version "1.24"
   id("us.ihmc.log-tools-plugin") version "0.6.3"
}

ihmc {
   group = "us.ihmc"
   version = "0.28.1"
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
   api("com.google.guava:guava:18.0")
   api("org.tukaani:xz:1.5")
   api("org.xerial.snappy:snappy-java:1.1.1-M1")
   api("net.jpountz.lz4:lz4:1.3.0")
   api("io.netty:netty-all:4.1.77.Final")
   api("org.openjdk.jol:jol-core:0.9")
   api("org.apache.commons:commons-text:1.9")

   api("us.ihmc:euclid:0.20.0")
   api("us.ihmc:ihmc-video-codecs:2.1.6")
   api("us.ihmc:ihmc-realtime:1.6.0")
   api("us.ihmc:ihmc-java-decklink-capture:0.4.0")
   api("us.ihmc:ihmc-pub-sub:0.18.5")
   api("us.ihmc:ihmc-pub-sub-serializers-extra:0.18.5")
   api("us.ihmc:ihmc-commons:0.32.0")
   api("us.ihmc:ihmc-graphics-description:0.20.3")
   api("us.ihmc:mecano:17-0.12.3")
   api("com.hierynomus:sshj:0.31.0")
   api("us.ihmc:scs2-definition:17-0.14.6")

   var javaFXVersion = "17.0.2"
   api(ihmc.javaFXModule("base", javaFXVersion))
   api(ihmc.javaFXModule("controls", javaFXVersion))
   api(ihmc.javaFXModule("graphics", javaFXVersion))
   api(ihmc.javaFXModule("fxml", javaFXVersion))
}

testDependencies {
   api("us.ihmc:ihmc-commons-testing:0.32.0")
}

app.entrypoint("IHMCLogger", "us.ihmc.robotDataLogger.logger.YoVariableLoggerDispatcher")
app.entrypoint("TestCapture", "us.ihmc.javadecklink.Capture")


tasks.register<JavaExec>("deploy") {
		dependsOn("generateMessages")
		dependsOn("distTar")
		group = "Deploy"
		description = "Deploy logger"
		classpath = sourceSets.main.get().runtimeClasspath
		main = "us.ihmc.publisher.logger.ui.LoggerDeployApplication"
		
		var p =   projectDir.toPath().resolve("build/distributions/" + project.name + "-" + project.version + ".tar").normalize()
		
		args("--logger-dist=" + p)
}	

tasks.create("generateMessages") {
   doLast {
      generateMessages()
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
