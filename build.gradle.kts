import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.security.MessageDigest
import java.io.FileInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.nmcp)
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
    signing
    jacoco
}

group = "io.github.texport"
version = "1.0.2"

repositories {
    mavenLocal()
    mavenCentral()
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = true
    autoCorrect = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

kotlin {
    jvm()
    
    val xcf = XCFramework("SuperkassaOfflineQueue")
    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SuperkassaOfflineQueue"
            xcf.add(this)
        }
    }

    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        commonMain {
            dependencies {
                // Core offline-queue logic has no external dependencies
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.slf4j.api)
            }
        }
        jvmTest {
            // JVM-specific tests
        }
    }

    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = libs.versions.java.get()
}

jacoco {
    toolVersion = libs.versions.jacocoVersion.get()
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

val jacocoTestReport: org.gradle.api.tasks.TaskProvider<JacocoReport> = tasks.register<JacocoReport>("jacocoTestReport") {
    description = "Generates Jacoco code coverage report for the JVM target."
    dependsOn(tasks.named("jvmTest"))
    classDirectories.setFrom(files(tasks.named("compileKotlinJvm")))
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    executionData.setFrom(files(layout.buildDirectory.file("jacoco/jvmTest.exec")))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        val javadocJarTask = tasks.register<Jar>("${name}JavadocJar") {
            description = "Generates Javadoc jar for publication ${this@configureEach.name}"
            archiveClassifier.set("javadoc")
            archiveAppendix.set(this@configureEach.name)
        }
        artifact(javadocJarTask)
        pom {
            name.set("superkassa-offline-queue")
            description.set("Offline command queue and synchronization logic for Superkassa")
            url.set("https://github.com/texport/superkassa-offline-queue")

            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }

            developers {
                developer {
                    id.set("sergeyivanov")
                    name.set("Sergey Ivanov")
                    email.set("ivanov.sergey.ekb@gmail.com")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/texport/superkassa-offline-queue.git")
                developerConnection.set("scm:git:ssh://github.com/texport/superkassa-offline-queue.git")
                url.set("https://github.com/texport/superkassa-offline-queue")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrEmpty() && !signingPassword.isNullOrEmpty()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    isRequired = false
    sign(publishing.publications)
}

nmcp {
    publishAllPublicationsToCentralPortal {
        username.set(project.findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
        password.set(project.findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
        publishingType.set("AUTOMATIC")
    }
}

tasks.register("generateSpmManifest") {
    group = "publishing"
    description = "Zips SuperkassaOfflineQueue XCFramework, calculates SHA-256 and writes Package.swift"
    dependsOn("assembleSuperkassaOfflineQueueReleaseXCFramework")

    doLast {
        val versionStr = project.version.toString()
        val repoUrl = "https://github.com/texport/superkassa-offline-queue"
        val zipName = "SuperkassaOfflineQueue.xcframework.zip"
        val outputDir = layout.buildDirectory.dir("XCFrameworks/release").get().asFile
        val xcframeworkDir = File(outputDir, "SuperkassaOfflineQueue.xcframework")
        val zipFile = File(outputDir, zipName)

        if (!xcframeworkDir.exists()) {
            throw GradleException("XCFramework not found at ${xcframeworkDir.absolutePath}")
        }

        // 1. Zipping XCFramework
        println("Zipping XCFramework to ${zipFile.absolutePath}...")
        zipFile.delete()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            xcframeworkDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(xcframeworkDir.parentFile).path
                    zos.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().buffered().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }

        // 2. Compute SHA-256
        println("Computing SHA-256 checksum...")
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(zipFile).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead = fis.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = fis.read(buffer)
            }
        }
        val checksumBytes = digest.digest()
        val checksum = checksumBytes.joinToString("") { "%02x".format(it) }
        println("SHA-256: $checksum")

        // 3. Write Package.swift
        val packageSwiftFile = rootProject.file("Package.swift")
        println("Writing Package.swift to ${packageSwiftFile.absolutePath}...")
        packageSwiftFile.writeText(
            """
            // swift-tools-version:5.5
            import PackageDescription

            let package = Package(
                name: "SuperkassaOfflineQueue",
                platforms: [
                    .iOS(.v15)
                ],
                products: [
                    .library(
                        name: "SuperkassaOfflineQueue",
                        targets: ["SuperkassaOfflineQueue"]
                    ),
                ],
                dependencies: [],
                targets: [
                    .binaryTarget(
                        name: "SuperkassaOfflineQueue",
                        url: "$repoUrl/releases/download/v$versionStr/$zipName",
                        checksum: "$checksum"
                    )
                ]
            )
            """.trimIndent() + "\n"
        )
        println("SPM manifest generation complete for version $versionStr!")
    }
}

