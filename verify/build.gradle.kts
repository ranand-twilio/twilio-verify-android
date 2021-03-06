/*
 * Copyright (c) 2020 Twilio Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//region Plugins
apply(from = "../jacoco.gradle.kts")
apply(from = "version.gradle.kts")
plugins {
  id(Config.Plugins.androidLibrary)
  id(Config.Plugins.kotlinAndroid)
  id(Config.Plugins.kotlinAndroidExtensions)
  id(Config.Plugins.dokka)
  id(MavenPublish.plugin)
  jacoco
  id(Config.Plugins.apkscale)
}
//endregion

val verifyVersionName: String by extra
val verifyVersionCode: String by extra
val baseURL: String by extra
//region Android
android {
  compileSdkVersion(Config.Versions.compileSDKVersion)

  defaultConfig {
    minSdkVersion(Config.Versions.minSDKVersion)
    targetSdkVersion(Config.Versions.targetSDKVersion)
    versionCode = verifyVersionCode.toInt()
    versionName = verifyVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
    buildConfigField("String", "BASE_URL", baseURL)
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  testOptions {
    unitTests.isIncludeAndroidResources = true
    unitTests.isReturnDefaultValues = true
  }
  lintOptions {
    lintConfig = rootProject.file(".lint/config.xml")
    xmlReport = true
    isCheckAllWarnings = true
  }
}
//endregion

//region KDoc
tasks.dokkaHtml {
  outputDirectory = "../docs/$verifyVersionName"
  disableAutoconfiguration = false
  dokkaSourceSets {
    configureEach {
      includeNonPublic = false
      reportUndocumented = true
      skipEmptyPackages = true
    }
  }

  doLast {
    ant.withGroovyBuilder {
      "copy"(
        "file" to "index.html",
        "todir" to "../docs/$verifyVersionName"
      )
    }
  }
}
//endregion

//region Publish
val pomPackaging: String by project
val pomGroup: String by project
val pomArtifactId: String by project

/*
 * Maven upload configuration that can be used for any maven repo
 */
tasks {
  "uploadArchives"(Upload::class) {
    repositories {
      withConvention(MavenRepositoryHandlerConvention::class) {
        mavenDeployer {
          withGroovyBuilder {
            MavenPublish.Bintray.repository(
              MavenPublish.Bintray.url to uri(
                MavenPublish.mavenRepo(project)
              )
            ) {
              MavenPublish.Bintray.authentication(
                MavenPublish.Bintray.userName to MavenPublish.mavenUsername(project),
                MavenPublish.Bintray.password to MavenPublish.mavenPassword(project)
              )
            }
          }
          pom.project {
            withGroovyBuilder {
              MavenPublish.Bintray.version(verifyVersionName)
              MavenPublish.Bintray.groupId(pomGroup)
              MavenPublish.Bintray.artifactId(pomArtifactId)
              MavenPublish.Bintray.packaging(pomPackaging)
            }
          }
        }
      }
    }
  }
}

val dokkaHtmlJar by tasks.creating(Jar::class) {
  dependsOn(tasks.dokkaHtml)
  from(
    tasks.dokkaHtml.get()
      .getOutputDirectoryAsFile()
  )
  archiveClassifier.set("html-doc")
}

val sourcesJar by tasks.creating(Jar::class) {
  archiveClassifier.set("sources")
  from(android.sourceSets.getByName("main").java.srcDirs)
}

artifacts {
  archives(dokkaHtmlJar)
  archives(sourcesJar)
}

task("bintrayLibraryReleaseCandidateUpload", GradleBuild::class) {
  description = "Publish Verify SDK release candidate to internal bintray"
  group = "Publishing"
  buildName = "Verify"
  buildFile = file("build.gradle.kts")
  tasks = listOf("assembleRelease", "uploadArchives")
  startParameter.projectProperties.plusAssign(
    gradle.startParameter.projectProperties + MavenPublish.Bintray.credentials(
      project,
      "https://api.bintray.com/maven/twilio/internal-releases/twilio-verify-android/;publish=1",
      MavenPublish.Bintray.user, MavenPublish.Bintray.apiKey
    )
  )
}

task("bintrayLibraryReleaseUpload", GradleBuild::class) {
  description = "Publish Verify SDK release to bintray"
  group = "Publishing"
  buildName = "Verify"
  buildFile = file("build.gradle.kts")
  tasks = listOf("assembleRelease", "uploadArchives")

  startParameter.projectProperties.plusAssign(
    gradle.startParameter.projectProperties + MavenPublish.Bintray.credentials(
      project,
      "https://api.bintray.com/maven/twilio/releases/twilio-verify-android/;publish=1",
      MavenPublish.Bintray.user, MavenPublish.Bintray.apiKey
    )
  )
}

apkscale {
  abis = setOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
}

task("generateSizeReport") {
  dependsOn("assembleRelease", "measureSize")
  description = "Calculate Verify SDK Size Impact"
  group = "Reporting"

  doLast {
    var sizeReport =
      "### Size impact\n" +
        "\n" +
        "| ABI             | APK Size Impact |\n" +
        "| --------------- | --------------- |\n"
    val apkscaleOutputFile = file("$buildDir/apkscale/build/outputs/reports/apkscale.json")
    val jsonSlurper = groovy.json.JsonSlurper()
    val apkscaleOutput = jsonSlurper.parseText(apkscaleOutputFile.readText()) as List<*>
    val releaseOutput = apkscaleOutput[0] as Map<*, *>
    val sizes = releaseOutput["size"] as Map<String, String>
    sizes.forEach { (arch, sizeImpact) ->
      sizeReport += "| ${arch.padEnd(16)}| ${sizeImpact.padEnd(16)}|\n"
    }
    val sizeReportDir = "$buildDir/outputs/sizeReport"
    mkdir(sizeReportDir)
    val targetFile = file("$sizeReportDir/${rootProject.name.capitalize()}SizeImpactReport.txt")
    targetFile.createNewFile()
    targetFile.writeText(sizeReport)
  }
}
//endregion

dependencies {
  val securityVersionName: String by rootProject.allprojects.first { it.name == Modules.security }.extra
  implementation(fileTree(mapOf("dir" to "libs", "includes" to listOf("*.jar"))))
  debugImplementation(project(":${Modules.security}"))
  releaseImplementation("com.twilio:twilio-security-android:$securityVersionName")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.72")
  androidTestImplementation("androidx.test.ext:junit:1.1.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
  androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.3.1")
  androidTestImplementation("com.squareup.okhttp3:okhttp-tls:4.3.1")
  androidTestImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
  testImplementation("junit:junit:4.12")
  testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0")
  testImplementation("org.robolectric:robolectric:4.4")
  testImplementation("androidx.test:core:1.2.0")
  testImplementation("org.hamcrest:hamcrest-library:1.3")
  testImplementation("org.mockito:mockito-inline:2.28.2")
}
