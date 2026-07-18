plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.baseerah"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.liquibase:liquibase-core")
    // JWT for phone+OTP auth (Phase 9): api on the compile path, impl/jackson at runtime only.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // ArchUnit (Phase 10.11): the dependency rule (api -> application -> domain) as a test-time gate.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The demo LoanRequestSeeder (Phase 12 / Step 12.5) commits loan requests into the runtime-populated
    // financing_* tables on boot. Tests run against the shared local Postgres, and several bank/financing
    // suites assert on the global contents of those tables (portfolio facility counts, a client's request
    // list), so a seeded row from any test boot would perturb them. Disable that one seeder for the whole
    // test run (leaving the persona/challenge seeders on) — the seeder is exercised via bootRun, not tests.
    systemProperty("baseerah.seed.loan-requests", "false")
}
