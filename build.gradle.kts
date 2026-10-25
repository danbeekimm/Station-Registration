// 루트는 모듈을 묶는 역할만 한다. 의존성과 빌드 설정은 각 모듈의 build.gradle.kts에 있다.
subprojects {
    group = "com.exem.dust"
    version = "0.1.0"
}
