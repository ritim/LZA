plugins {
	java
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lza"
version = "0.0.1-SNAPSHOT"
description = "AetherCare AI Home Care Copilot MVP"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

extra["lombok.version"] = "1.18.36"
// Spring Cloud BOM：2025.0.0 對應 Spring Boot 3.5（spring-cloud-vault-config 4.2.x）
extra["springCloudVersion"] = "2025.0.0"

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	// Vault secrets management（透過 application-vault.yml 啟用，不啟用時不影響運作）
	implementation("org.springframework.cloud:spring-cloud-starter-vault-config")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	implementation("org.liquibase:liquibase-core")
	implementation("org.springframework.kafka:spring-kafka")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:kafka")
	testImplementation("com.redis:testcontainers-redis:2.2.2")
	testImplementation("org.awaitility:awaitility")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
