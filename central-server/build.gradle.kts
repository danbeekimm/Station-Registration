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

// 제출용 db/schema.sql을 그대로 클래스패스(db/schema.sql)에 넣어 서버가 기동 시 같은 파일을 실행한다.
tasks.processResources {
    from(rootProject.file("db/schema.sql")) {
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
