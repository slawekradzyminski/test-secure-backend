package com.awesome.testing.controller.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUnavailableTest {

    @Test
    void recognisesHikariTimeoutBehindTheTransactionManager() {
        // What JpaTransactionManager throws when HikariCP has no connection within connectionTimeout.
        Throwable failure = new CannotCreateTransactionException("Could not open JPA EntityManager for transaction",
                new SQLTransientConnectionException("HikariPool-1 - Connection is not available, request timed out after 5000ms"));

        assertThat(DatabaseUnavailable.isCause(failure)).isTrue();
    }

    @Test
    void recognisesConnectionExceptionSqlStatesDeepInTheChain() {
        Throwable failure = new RuntimeException("wrapped",
                new IllegalStateException("again", new SQLException("Connection refused", "08001")));

        assertThat(DatabaseUnavailable.isCause(failure)).isTrue();
    }

    @Test
    void recognisesARollbackThatFailedOnADeadConnection() {
        // What the lab's postgres-down produced: the query hit a dead pooled connection, then the rollback failed.
        Throwable failure = new org.springframework.orm.jpa.JpaSystemException(new RuntimeException(
                "Unable to rollback against JDBC Connection", new org.hibernate.TransactionException(
                        "Unable to rollback against JDBC Connection", new SQLException("Connection is closed"))));

        assertThat(DatabaseUnavailable.isCause(failure)).isTrue();
        assertThat(DatabaseUnavailable.isCause(new org.hibernate.TransactionException("no JDBC cause"))).isFalse();
    }

    @Test
    void recognisesSpringResourceFailures() {
        assertThat(DatabaseUnavailable.isCause(new DataAccessResourceFailureException("down"))).isTrue();
        assertThat(DatabaseUnavailable.isCause(new org.hibernate.exception.JDBCConnectionException(
                "broken", new SQLException("I/O error", "08006")))).isTrue();
    }

    @Test
    void ignoresOtherDatabaseErrors() {
        assertThat(DatabaseUnavailable.isCause(new DataIntegrityViolationException("duplicate",
                new SQLException("duplicate key", "23505")))).isFalse();
        assertThat(DatabaseUnavailable.isCause(new CannotAcquireLockException("deadlock",
                new SQLException("deadlock detected", "40P01")))).isFalse();
        assertThat(DatabaseUnavailable.isCause(new SQLException("no state"))).isFalse();
        assertThat(DatabaseUnavailable.isCause(new RuntimeException("Boom"))).isFalse();
        assertThat(DatabaseUnavailable.isCause(null)).isFalse();
    }
}
