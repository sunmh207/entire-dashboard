plugins {
	java
	id("org.springframework.boot") version "4.1.0-M2"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.mzfuture.entire"
version = "0.0.1-SNAPSHOT"
description = "Dashboard for entireio/cli"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot Starters
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("net.bytebuddy:byte-buddy")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// Flyway
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")

	// MySQL JDBC
	runtimeOnly("com.mysql:mysql-connector-j")

	// JWT
	implementation("com.auth0:java-jwt:4.4.0")

	// SpringDoc OpenAPI (Swagger UI)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

	// Querydsl
	implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// MapStruct
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

	// Other Utilities
	implementation("cn.hutool:hutool-all:5.8.42")

	// JGit - Java Git操作库
	implementation("org.eclipse.jgit:org.eclipse.jgit:7.5.0.202512021534-r")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
