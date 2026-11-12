package com.awesome.testing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Deletes a user in its own transaction and retries once when PostgreSQL aborts it as a deadlock or
 * lock failure. {@link UserService#forget} takes its locks in a consistent order; the retry is the last
 * resort for a lock path that is not covered (a deleted user's rows are written by many features).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeletion {

    static final int ATTEMPTS = 2;

    private final UserService userService;

    public void forget(String username) {
        for (int attempt = 1; ; attempt++) {
            try {
                userService.forget(username);
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= ATTEMPTS) {
                    throw e;
                }
                log.warn("Deleting user {} lost a lock conflict, retrying once", username, e);
            }
        }
    }
}
