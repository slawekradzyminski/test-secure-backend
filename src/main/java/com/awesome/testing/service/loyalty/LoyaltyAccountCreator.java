package com.awesome.testing.service.loyalty;

import com.awesome.testing.entity.loyalty.LoyaltyAccountEntity;
import com.awesome.testing.repository.loyalty.LoyaltyAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a loyalty account in its own transaction, so two concurrent first
 * earnings for one user cannot both insert it: the loser's duplicate-key error
 * is confined to this short transaction and the caller then locks the row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoyaltyAccountCreator {

    private final LoyaltyAccountRepository accountRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureExists(String username) {
        if (accountRepository.existsById(username)) {
            return;
        }
        try {
            accountRepository.saveAndFlush(LoyaltyAccountEntity.builder()
                    .username(username)
                    .balance(0L)
                    .lifetimeEarned(0L)
                    .build());
        } catch (DataIntegrityViolationException alreadyCreated) {
            // Another transaction created it first; the caller locks that row.
            log.debug("Loyalty account of {} was created concurrently", username);
        }
    }
}
