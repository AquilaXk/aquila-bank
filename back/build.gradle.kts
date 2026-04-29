import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    java
    jacoco
    id("com.diffplug.spotless") version "8.4.0"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "aquila-bank"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val jacocoStaticCoverageExclusions =
    listOf(
        "com/aquilabank/AquilaBankApplication.class",
        "com/aquilabank/domain/**/port/**",
        "com/aquilabank/domain/**/*UseCase.class",
        "com/aquilabank/**/*Configuration.class",
        "com/aquilabank/**/*Properties.class",
        "com/aquilabank/**/*Exception.class",
    )

val jacocoCoverageBaselineExclusions =
    layout.projectDirectory.file("config/jacoco-coverage-baseline-excludes.txt")

val integrationTestIncludes = listOf("**/*IntegrationTest.class", "**/*RuntimeSmokeTest.class")

val queryPlanTestIncludes =
    listOf(
        "**/JdbcTransactionReadRepositoryBaselineIntegrationTest.class",
        "**/TransactionDatasourceStatementTimeoutIntegrationTest.class",
        "**/TransactionJdbcQueryTimeoutIntegrationTest.class",
        "**/JdbcTransactionReadRepositoryPartitionFitIntegrationTest.class",
        "**/TransactionQueryConcurrencySloIntegrationTest.class",
    )

val fastTestTaskNames = listOf("test", "integrationTest")
val fullTestTaskNames = fastTestTaskNames + "queryPlanTest"

fun jacocoCoverageExclusions() =
    jacocoStaticCoverageExclusions +
        jacocoCoverageBaselineExclusions.asFile
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

fun jacocoMainClassDirectories() =
    files(
        sourceSets.main.get().output.classesDirs.files.map {
            fileTree(it) {
                // 기존 미커버 baseline을 파일로 고정해 신규 코드의 100% 게이트를 유지한다.
                exclude(jacocoCoverageExclusions())
            }
        })

fun jacocoExecutionDataFor(taskNames: List<String>) =
    fileTree(layout.buildDirectory.dir("jacoco")) { include(taskNames.map { "$it.exec" }) }

fun JacocoCoverageVerification.configureLineCoverageRule() {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
}

tasks.named<Test>("test") {
    description = "Runs unit and lightweight slice tests."
    exclude(integrationTestIncludes)
}

val integrationTest by tasks.registering(Test::class) {
    description = "Runs Spring/Testcontainers integration tests except transaction query plan gates."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include(integrationTestIncludes)
    exclude(queryPlanTestIncludes)
    shouldRunAfter(tasks.named("test"))
}

val queryPlanTest by tasks.registering(Test::class) {
    description = "Runs bounded transaction query plan and timeout regression gates."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include(queryPlanTestIncludes)
    shouldRunAfter(integrationTest)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(fullTestTaskNames)
    classDirectories.setFrom(jacocoMainClassDirectories())
    executionData.setFrom(jacocoExecutionDataFor(fullTestTaskNames))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoReport>("jacocoPrReport") {
    dependsOn(fastTestTaskNames)
    classDirectories.setFrom(jacocoMainClassDirectories())
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    executionData.setFrom(jacocoExecutionDataFor(fullTestTaskNames))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/pr/jacocoPrReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/pr/html"))
    }
}

tasks.register<JacocoReport>("jacocoFullTestReport") {
    dependsOn(fullTestTaskNames)
    classDirectories.setFrom(sourceSets.main.get().output.classesDirs)
    sourceDirectories.setFrom(sourceSets.main.get().allSource.srcDirs)
    executionData.setFrom(jacocoExecutionDataFor(fullTestTaskNames))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/full/jacocoFullTestReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/full/html"))
    }
}

tasks.register<JacocoCoverageVerification>("ciFastCoverageVerification") {
    dependsOn(fastTestTaskNames)
    classDirectories.setFrom(jacocoMainClassDirectories())
    executionData.setFrom(jacocoExecutionDataFor(fastTestTaskNames))
    configureLineCoverageRule()
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
    classDirectories.setFrom(jacocoMainClassDirectories())
    executionData.setFrom(jacocoExecutionDataFor(fullTestTaskNames))
    configureLineCoverageRule()
}

tasks.register("ciFastCheck") {
    description = "Runs PR fast checks without bounded transaction query plan gates."
    group = "verification"
    dependsOn("test", integrationTest, "jacocoPrReport", "ciFastCoverageVerification", "spotlessCheck")
}

tasks.named("check") {
    dependsOn(integrationTest, queryPlanTest)
    dependsOn(tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification"))
}

spotless {
    java {
        target("src/*/java/**/*.java")
        googleJavaFormat()
        formatAnnotations()
    }
}
