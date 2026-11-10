package com.awesome.testing.service.loyalty;

import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import com.awesome.testing.repository.loyalty.LoyaltyAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoyaltyAccountCreatorTest {

    @Mock
    private LoyaltyAccountRepository accountRepository;

    @InjectMocks
    private LoyaltyAccountCreator creator;

    @Test
    void createsAnEmptyAccountOnce() {
        when(accountRepository.existsById("new")).thenReturn(false);

        creator.ensureExists("new");

        ArgumentCaptor<LoyaltyAccountEntity> captor = ArgumentCaptor.forClass(LoyaltyAccountEntity.class);
        verify(accountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("new");
        assertThat(captor.getValue().getBalance()).isZero();
        assertThat(captor.getValue().getLifetimeEarned()).isZero();
    }

    @Test
    void keepsExistingAccounts() {
        when(accountRepository.existsById("old")).thenReturn(true);

        creator.ensureExists("old");

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void toleratesAConcurrentCreation() {
        when(accountRepository.existsById("race")).thenReturn(false);
        when(accountRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatCode(() -> creator.ensureExists("race")).doesNotThrowAnyException();
    }
}
