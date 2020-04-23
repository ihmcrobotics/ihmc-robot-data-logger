import us.ihmc.idl.generator.IDLGenerator

buildscript {
   dependencies {
      classpath("us.ihmc:ihmc-pub-sub-generator:0.14.0")
   }
}

plugins {
   id("us.ihmc.ihmc-build") version "0.20.1"
   id("us.ihmc.ihmc-ci") version "5.3"
   id("us.ihmc.ihmc-cd") version "1.14"
   id("us.ihmc.log-tools") version "0.4.1"
}

ihmc {
   group = "us.ihmc"
   version = "0.16.0"
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
   api("org.apache.commons:commons-lang3:3.8.1")
   api("com.google.guava:guava:18.0")
   api("commons-io:commons-io:2.6")
   api("org.tukaani:xz:1.5")
   api("org.xerial.snappy:snappy-java:1.1.1-M1")
   api("net.jpountz.lz4:lz4:1.3.0")
   api("io.netty:netty-all:4.1.32.Final")
   api("org.openjdk.jol:jol-core:0.9")

   api("us.ihmc:euclid:0.13.1")
   api("us.ihmc:ihmc-yovariables:0.5.0")
   api("us.ihmc:ihmc-video-codecs:2.1.5")
   api("us.ihmc:ihmc-realtime:1.3.0")
   api("us.ihmc:ihmc-java-decklink-capture:0.3.3")
   api("us.ihmc:ihmc-pub-sub:0.14.0")
   api("us.ihmc:ihmc-pub-sub-serializers-extra:0.14.0")
   api("us.ihmc:ihmc-commons:0.28.2")
   api("us.ihmc:ihmc-graphics-description:0.15.0")
   api("us.ihmc:mecano:0.3.0")
}

testDependencies {
   api("us.ihmc:ihmc-commons-testing:0.28.2")
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

val loggerDirectory = "/home/shadylady/IHMCLogger"

fun deployLogger()
{
   remote.session("logger", "shadylady")
   {
      exec("mkdir -p $loggerDirectory")

      exec("rm -rf $loggerDirectory/bin")
      exec("rm -rf $loggerDirectory/lib")

      put(file("build/install/ihmc-robot-data-logger/bin").toString(), "$loggerDirectory/bin")
      put(file("build/install/ihmc-robot-data-logger/lib").toString(), "$loggerDirectory/lib")

      exec("chmod +x $loggerDirectory/bin/IHMCLogger")
   }
}


//if (ihmc.isBuildRoot())
//{
//   task loggerStartScripts(type: org.gradle.jvm.application.tasks.CreateStartScripts) {
//      outputDir = file("build/scripts")
//      mainClassName = "us.ihmc.robotDataLogger.logger.YoVariableLoggerDispatcher"
//      applicationName = "IHMCLogger"
//      classpath = project.configurations.runtime + jar.outputs.files
//   }
//
//   task generateMessages(type: us.ihmc.idl.generator.IDLGeneratorTask) {
//      idlFiles = fileTree(dir: "src/main/idl")
//      includeDirs = files(".")
//      targetDirectory = file("src/main/java-generated")
//      packagePrefix = ""
//   }

//   compileJava.dependsOn generateMessages

//   distributions {
//      logger {
//         baseName = "IHMCLogger"
//         contents {
//            into("lib") {
//               from project.configurations.runtime + jar.outputs.files
//            }
//
//            into("bin") {
//               from loggerStartScripts
//               include "IHMCLogger*"
//            }
//         }
//      }
//   }
//
//   task setupDeployLoggerRemote {
//      doLast {
//         remotes.create("deployLoggerTarget") {
//            host = deployLoggerHost
//            user = deployLoggerUser
//            password = deployLoggerPassword
//            knownHosts = allowAnyHosts
//         }
//      }
//   }
//
//   task deployLogger(dependsOn: [loggerDistTar, setupDeployLoggerRemote]) {
//      doLast {
//         ssh.run {
//            session(remotes.deployLoggerTarget) {
//               project.logger.lifecycle("Copying Logger distribution tarball to remote host")
//               def distTarFile = loggerDistTar.outputs.files.singleFile
//               put from: distTarFile, into: "."
//
//               project.logger.lifecycle("Untarring distribution on remote host")
//               execute "tar xf ./${distTarFile.name}"
//               project.logger.lifecycle("Removing tarball from remote host")
//               execute "rm -f ./${distTarFile.name}"
//               project.logger.lifecycle("Removing old version")
//               execute "rm -rf IHMCLogger"
//               project.logger.lifecycle("Moving Logger distribution in to place")
//               execute "mv ./${distTarFile.name.replace(".tar", "")} IHMCLogger"
//               project.logger.lifecycle("Logger deployment to remote host complete!")
//            }
//         }
//      }
//   }
//
//   task checkThatDistributionDoesntAlreadyExist(type: Exec) {
//      def distTarFile = loggerDistTar.outputs.files.singleFile
//      workingDir project.projectDir
//      executable "curl"
//      args = ["--write-out", "%{http_code}", "--silent", "--output", "/dev/null", "--head", "https://dl.bintray.com/ihmcrobotics/distributions/${distTarFile.name}"]
//      standardOutput = new ByteArrayOutputStream();
//
//      doLast {
//         execResult.assertNormalExitValue()
//         def output = standardOutput.toString()
//         if (output.equals("200"))
//         {
//            throw new GradleException("Distribution ${distTarFile.name} already exists on Bintray. Distributions versions should not be overwritten. Did you mean to release a new version or hotfix?")
//         }
//      }
//   }
//
//   task publishLoggerDistributionToBintray(type: Exec, dependsOn: [checkThatDistributionDoesntAlreadyExist, loggerDistTar]) {
//      def distTarFile = loggerDistTar.outputs.files.singleFile
//
//      workingDir project.projectDir
//      executable "curl"
//      args = ["--write-out", "%{http_code}", "--silent", "--output", "/dev/null", "-T", distTarFile.canonicalPath, "-u${bintray_user}:${bintray_key}", "https://api.bintray.com/content/ihmcrobotics/distributions/IHMCLogger/${project.version}/${distTarFile.name}?publish=1"]
//      standardOutput = new ByteArrayOutputStream();
//
//      doLast {
//         execResult.assertNormalExitValue()
//         def output = standardOutput.toString()
//         if (!output.equals("201"))
//         {
//            throw new GradleException("Upload failed! HTTP Response code: ${output}.")
//         }
//      }
//   }
//}