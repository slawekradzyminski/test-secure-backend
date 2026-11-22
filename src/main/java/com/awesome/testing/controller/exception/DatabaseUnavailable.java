package com.awesome.testing.controller.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

/**
 * Recognises "the database cannot be reached" in an exception chain: no pooled connection within
 * HikariCP's connection timeout, a refused or broken connection (SQLState class {@code 08}), or
 * Spring's resource-failure wrappers around them, or a commit or rollback that failed on the JDBC
 * connection (a query that hit a dead pooled connection fails, and its rollback then fails with
 * HikariCP's "Connection is closed", which carries no SQLState). Such failures answer
 * {@code 503 Database unavailable} instead of a generic 500, so callers know a retry may work.
 */
public final class DatabaseUnavailable {

    public static final String MESSAGE = "Database unavailable";
    private static final String CONNECTION_EXCEPTION_CLASS = "08";
    private static final int MAX_DEPTH = 16;

    private DatabaseUnavailable() {
    }

    public static boolean isCause(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_DEPTH; depth++) {
            if (current instanceof CannotCreateTransactionException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof SQLTransientConnectionException
                    || current instanceof org.hibernate.exception.JDBCConnectionException) {
                return true;
            }
            if (current instanceof org.hibernate.TransactionException && current.getCause() instanceof SQLException) {
                return true;
            }
            if (current instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith(CONNECTION_EXCEPTION_CLASS)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
