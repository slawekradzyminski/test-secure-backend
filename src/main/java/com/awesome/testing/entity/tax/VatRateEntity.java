package com.awesome.testing.entity.tax;

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

/** Standard VAT rate of a country in percent; 0 means VAT is not applicable (migration V13). */
@Entity
@Table(name = "vat_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VatRateEntity {

    /** ISO 3166-1 alpha-2, upper case. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(name = "country_name", length = 64, nullable = false)
    private String countryName;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
