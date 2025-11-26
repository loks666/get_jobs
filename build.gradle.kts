plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.superxiang"
version = "v2.0.1"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // JSON
    implementation("org.json:json:20231013")
    
    // Playwright
    implementation("com.microsoft.playwright:playwright:1.51.0")
    
    // Selenium
    implementation("org.seleniumhq.selenium:selenium-java:4.31.0") {
        exclude(group = "org.seleniumhq.selenium", module = "selenium-devtools-v133")
        exclude(group = "org.seleniumhq.selenium", module = "selenium-devtools-v134")
        exclude(group = "org.seleniumhq.selenium", module = "selenium-edge-driver")
        exclude(group = "org.seleniumhq.selenium", module = "selenium-firefox-driver")
        exclude(group = "org.seleniumhq.selenium", module = "selenium-ie-driver")
        exclude(group = "org.seleniumhq.selenium", module = "selenium-safari-driver")
    }
    implementation("org.seleniumhq.selenium:selenium-api:4.31.0")
    implementation("org.seleniumhq.selenium:selenium-devtools-v135:4.31.0")
    implementation("org.seleniumhq.selenium:selenium-chrome-driver:4.31.0")
    implementation("org.seleniumhq.selenium:selenium-remote-driver:4.31.0")
    implementation("org.seleniumhq.selenium:selenium-support:4.31.0")
    implementation("org.seleniumhq.selenium:selenium-chromium-driver:4.31.0")
    
    // Jackson YAML
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    
    // Apache HttpClient
    implementation("org.apache.httpcomponents.client5:httpclient5-fluent:5.1")
    
    // Dotenv
    implementation("io.github.cdimascio:dotenv-java:2.2.0")
    
    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    
    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test {
    useJUnitPlatform()
}

// 显示已过时 API 的详细告警
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation"))
    options.encoding = "UTF-8"
}

// Spring Boot 构建信息
springBoot {
    buildInfo()
    mainClass.set("StartAll")
}

// 配置 bootRun
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
}

// 创建运行Boss类的任务
tasks.register<JavaExec>("runBoss") {
    group = "application"
    description = "Run Boss main class"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("boss.Boss")
    standardInput = System.`in`
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
}
