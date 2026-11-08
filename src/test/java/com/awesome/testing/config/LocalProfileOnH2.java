package com.awesome.testing.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Keeps unit tests that activate the {@code local} profile on in-memory H2.
 * The packaged {@code local} profile uses PostgreSQL with Flyway; test property sources
 * outrank every profile document, so this pins the unit-test database regardless of profile order.
 * RuntimeProfilesPostgresIT covers the real profile on PostgreSQL.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ActiveProfiles({"test", "local"})
@LocalProfileOnH2
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=sa",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public @interface LocalProfileOnH2 {
}
