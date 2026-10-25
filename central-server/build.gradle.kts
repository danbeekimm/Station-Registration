// 중앙 서버. TCP 수신, 경보 판정, DB 기록, Slack 통보를 담당하는 Spring Boot 애플리케이션.
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// bootRun·feasibility·registerStation 등 JVM을 띄우는 태스크의 콘솔 출력을 UTF-8로 고정한다.
// Gradle이 띄운 JVM의 stdout은 파이프라 Windows에서는 기본값이 CP949가 되어 한글 로그가 깨진다.
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// 제출용 db/schema.sql·seed.sql을 그대로 클래스패스(db/)에 넣어 서버가 기동 시 같은 파일을 실행한다.
tasks.processResources {
    from(rootProject.file("db")) {
        include("schema.sql", "seed.sql")
        into("db")
    }
}

tasks.test {
    useJUnitPlatform()
}

// 운영 중 측정소 등록. 서버 재기동 없이 station 테이블에 직접 upsert한다.
//   ./gradlew registerStation --code=111191 --name=은평구 --token=<토큰>
abstract class RegisterStationTask : JavaExec() {
    @get:Input
    @get:Option(option = "code", description = "측정소 코드")
    abstract val code: Property<String>

    @get:Input
    @get:Option(option = "name", description = "측정소 이름")
    abstract val stationName: Property<String>

    @get:Input
    @get:Option(option = "token", description = "측정소 인증 토큰(평문). DB에는 해시만 저장된다")
    abstract val token: Property<String>

    override fun exec() {
        args("--code=${code.get()}", "--name=${stationName.get()}", "--token=${token.get()}")
        super.exec()
    }
}

tasks.register<RegisterStationTask>("registerStation") {
    group = "dustwatch"
    description = "측정소를 station 테이블에 등록한다 (--code, --name, --token)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.exem.dust.admin.RegisterStationCommand"
}

// 발령 가능성 검토. 서버 없이 데이터 파일만 읽어 Markdown을 출력한다.
//   ./gradlew -q feasibility
tasks.register<JavaExec>("feasibility") {
    group = "dustwatch"
    description = "제공 데이터로 경보 등급별 발령 가능성을 계산해 Markdown으로 출력한다"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.exem.dust.report.FeasibilityReport"
    workingDir = rootProject.projectDir
}

// 미등록 측정소 데모용 가상 데이터 생성. 제공 파일들의 시간별 평균에 ±10% 잡음을 섞어 같은 형식으로 쓴다.
//   ./gradlew makeMockStation --code=111191 --name=은평구
abstract class MakeMockStationTask : JavaExec() {
    @get:Input
    @get:Option(option = "code", description = "생성할 측정소 코드")
    abstract val code: Property<String>

    @get:Input
    @get:Option(option = "name", description = "생성할 측정소 이름")
    abstract val stationName: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "out", description = "출력 파일 (기본 data/<이름>.json)")
    abstract val out: Property<String>

    @get:Input
    @get:Optional
    @get:Option(option = "sources", description = "원본 파일 목록, 쉼표 구분 (기본 data/노원구.json,data/중구.json,data/강서구.json)")
    abstract val sources: Property<String>

    override fun exec() {
        args("--code=${code.get()}", "--name=${stationName.get()}")
        if (out.isPresent) args("--out=${out.get()}")
        if (sources.isPresent) args("--sources=${sources.get()}")
        super.exec()
    }
}

tasks.register<MakeMockStationTask>("makeMockStation") {
    group = "dustwatch"
    description = "가상 측정소 데이터 파일을 생성한다 (--code, --name, [--out], [--sources])"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.exem.dust.tools.MockStationGenerator"
    workingDir = rootProject.projectDir
}
