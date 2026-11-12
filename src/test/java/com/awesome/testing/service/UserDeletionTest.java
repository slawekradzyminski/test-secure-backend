package com.awesome.testing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserDeletionTest {

    @Mock
    private UserService userService;

    @Test
    void aDeadlockVictimIsRetriedOnce() {
        doThrow(new CannotAcquireLockException("deadlock detected")).doNothing().when(userService).forget("client");

        new UserDeletion(userService).forget("client");

        verify(userService, times(2)).forget("client");
    }

    @Test
    void aSecondLockFailureIsReported() {
        doThrow(new CannotAcquireLockException("deadlock detected")).when(userService).forget("client");

        assertThatThrownBy(() -> new UserDeletion(userService).forget("client"))
                .isInstanceOf(CannotAcquireLockException.class);
        verify(userService, times(UserDeletion.ATTEMPTS)).forget("client");
    }

    @Test
    void otherFailuresAreNotRetried() {
        doThrow(new DataIntegrityViolationException("fk")).when(userService).forget("client");

        assertThatThrownBy(() -> new UserDeletion(userService).forget("client"))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(userService, times(1)).forget("client");
    }
}
