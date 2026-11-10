package com.awesome.testing.service.tax;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.tax.VatRateRequestDto;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.repository.tax.VatRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VatRateServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);

    @Mock private VatRateRepository repository;
    private VatRateService service;

    @BeforeEach
    void setUp() {
        service = new VatRateService(repository, CLOCK);
        lenient().when(repository.save(any(VatRateEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void listsRatesOrderedByCountryCode() {
        when(repository.findAll(Sort.by("countryCode"))).thenReturn(List.of(rate("DE", "19.00"), rate("PL", "23.00")));

        assertThat(service.list()).extracting("countryCode").containsExactly("DE", "PL");
    }

    @Test
    void findsARateByNameOrVariant() {
        when(repository.findById("GB")).thenReturn(Optional.of(rate("GB", "20.00")));

        assertThat(service.get("Great Britain").getRate()).isEqualByComparingTo("20.00");
        assertThat(service.rateFor("uk")).map(VatRateEntity::getCountryCode).contains("GB");
    }

    @Test
    void unknownCountriesAreNotFound() {
        assertThatThrownBy(() -> service.get("Atlantis"))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("VAT rate not found for country: Atlantis");
                });
        assertThat(service.rateFor(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void recognisedCountriesWithoutARateAreNotFound() {
        when(repository.findById("JP")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("jp")).isInstanceOf(CustomException.class);
    }

    @Test
    void createsANewRate() {
        when(repository.findById("JP")).thenReturn(Optional.empty());

        VatRateUpsert result = service.upsert("jp", request(" Japan ", "10"));

        assertThat(result.created()).isTrue();
        assertThat(result.rate().getCountryCode()).isEqualTo("JP");
        assertThat(result.rate().getCountryName()).isEqualTo("Japan");
        assertThat(result.rate().getRate()).hasToString("10.00");
        assertThat(result.rate().getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    @Test
    void updatesAnExistingRateAndMapsUkToGb() {
        VatRateEntity existing = rate("GB", "20.00");
        when(repository.findById("GB")).thenReturn(Optional.of(existing));

        VatRateUpsert result = service.upsert("UK", request("United Kingdom", "17.5"));

        ArgumentCaptor<VatRateEntity> saved = ArgumentCaptor.forClass(VatRateEntity.class);
        verify(repository).save(saved.capture());
        assertThat(result.created()).isFalse();
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getRate()).hasToString("17.50");
        assertThat(existing.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 12, 0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "P", "POL", "P1", "Poland"})
    void rejectsCodesThatAreNotTwoLetters(String code) {
        assertThatThrownBy(() -> service.upsert(code, request("Poland", "23")))
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("Country code must be an ISO 3166-1 alpha-2 code");
                });
        verify(repository, never()).save(any());
    }

    private static VatRateEntity rate(String code, String rate) {
        return VatRateEntity.builder().countryCode(code).countryName(code).rate(new BigDecimal(rate))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
    }

    private static VatRateRequestDto request(String name, String rate) {
        return VatRateRequestDto.builder().countryName(name).rate(new BigDecimal(rate)).build();
    }
}
