plugins {
    // JDK 25가 없는 환경에서도 Gradle이 툴체인을 내려받아 빌드할 수 있게 한다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dustwatch"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("common", "station-simulator", "central-server")
