package com.awesome.testing.repository.tax;

import com.awesome.testing.entity.tax.VatRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VatRateRepository extends JpaRepository<VatRateEntity, String> {
}
