package com.awesome.testing.entity.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A display currency; {@code rateToUsd} is the USD value of one unit (migration V13). */
@Entity
@Table(name = "currency_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRateEntity {

    /** ISO 4217, upper case. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3, nullable = false)
    private String code;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(length = 8, nullable = false)
    private String symbol;

    @Column(name = "rate_to_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal rateToUsd;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
