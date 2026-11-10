package com.awesome.testing.service.tax;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.tax.VatRateDto;
import com.awesome.testing.dto.tax.VatRateRequestDto;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.repository.tax.VatRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** VAT rates by country. Orders copy the rate at checkout, so changes affect only new orders. */
@Service
@RequiredArgsConstructor
public class VatRateService {

    private static final Pattern ALPHA_2 = Pattern.compile("^[A-Za-z]{2}$");

    private final VatRateRepository vatRateRepository;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public List<VatRateDto> list() {
        return vatRateRepository.findAll(Sort.by("countryCode")).stream().map(VatRateDto::from).toList();
    }

    /** The rate for a country given by code or name; 404 when it is unknown or has no rate. */
    @Transactional(readOnly = true)
    public VatRateDto get(String country) {
        return rateFor(country).map(VatRateDto::from).orElseThrow(() -> notFound(country));
    }

    /** The rate for a free-text country, empty when the country is not recognised or has no rate. */
    @Transactional(readOnly = true)
    public Optional<VatRateEntity> rateFor(String country) {
        return CountryResolver.toIsoCode(country).flatMap(vatRateRepository::findById);
    }

    @Transactional
    public VatRateUpsert upsert(String countryCode, VatRateRequestDto request) {
        if (countryCode == null || !ALPHA_2.matcher(countryCode).matches()) {
            throw new CustomException("Country code must be an ISO 3166-1 alpha-2 code", HttpStatus.BAD_REQUEST);
        }
        String code = CountryResolver.toIsoCode(countryCode).orElseThrow();
        Optional<VatRateEntity> existing = vatRateRepository.findById(code);
        VatRateEntity rate = existing.orElseGet(() -> VatRateEntity.builder().countryCode(code).build());
        rate.setCountryName(request.getCountryName().trim());
        rate.setRate(request.getRate().setScale(2, RoundingMode.HALF_UP));
        rate.setUpdatedAt(LocalDateTime.now(commerceClock));
        return new VatRateUpsert(VatRateDto.from(vatRateRepository.save(rate)), existing.isEmpty());
    }

    static CustomException notFound(String country) {
        return new CustomException("VAT rate not found for country: " + country, HttpStatus.NOT_FOUND);
    }
}
