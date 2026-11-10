package com.awesome.testing.repository.currency;

import com.awesome.testing.entity.currency.CurrencyRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRateRepository extends JpaRepository<CurrencyRateEntity, String> {
}
