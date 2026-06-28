plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.nmcp)
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
    signing
    jacoco
}

group = "io.github.texport"
version = "1.0.1"

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
    
    iosArm64()
    iosX64()
    iosSimulatorArm64()

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

val jacocoTestReport = tasks.register<JacocoReport>("jacocoTestReport") {
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
    sign(publishing.publications)
}

nmcp {
    publishAllPublicationsToCentralPortal {
        username.set(project.findProperty("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
        password.set(project.findProperty("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
        publishingType.set("AUTOMATIC")
    }
}

