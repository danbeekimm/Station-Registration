// 시뮬레이터와 중앙 서버가 공유하는 프로토콜 메시지, NDJSON 코덱, 원본 데이터 파서.
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    // Spring Boot BOM은 버전 정렬에만 쓴다. 이 모듈에 Spring 의존은 없다.
    api(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    api("tools.jackson.core:jackson-databind")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
