package com.awesome.testing.service.currency;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.currency.CurrencyConversionDto;
import com.awesome.testing.dto.currency.CurrencyDto;
import com.awesome.testing.dto.currency.CurrencyRequestDto;
import com.awesome.testing.entity.currency.CurrencyRateEntity;
import com.awesome.testing.repository.currency.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Display currencies with fixed demo rates. Prices and orders stay in USD. */
@Service
@RequiredArgsConstructor
public class CurrencyService {

    public static final String BASE_CURRENCY = "USD";
    static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");
    private static final int MONEY_SCALE = 2;
    private static final Pattern CODE = Pattern.compile("^[A-Za-z]{3}$");
    private static final Pattern NUMBER = Pattern.compile("^\\+?[0-9]+(\\.[0-9]+)?$");
    private static final Pattern NEGATIVE_NUMBER = Pattern.compile("^-[0-9]+(\\.[0-9]+)?$");

    private final CurrencyRateRepository currencyRateRepository;
    private final Clock commerceClock;

    @Transactional(readOnly = true)
    public List<CurrencyDto> list() {
        return currencyRateRepository.findAll(Sort.by("code")).stream().map(CurrencyDto::from).toList();
    }

    @Transactional(readOnly = true)
    public CurrencyConversionDto convert(String amount, String from, String to) {
        BigDecimal value = parseAmount(amount);
        CurrencyRateEntity source = find(from == null ? BASE_CURRENCY : from, "from");
        CurrencyRateEntity target = find(to, "to");
        return CurrencyConversionDto.builder()
                .amount(value)
                .from(source.getCode())
                .to(target.getCode())
                .converted(CurrencyConverter.convert(value, source.getRateToUsd(), target.getRateToUsd()))
                .rate(CurrencyConverter.rate(source.getRateToUsd(), target.getRateToUsd()))
                .build();
    }

    @Transactional
    public CurrencyUpsert upsert(String code, CurrencyRequestDto request) {
        String normalised = normaliseCode(code, "Currency code");
        BigDecimal rate = request.getRateToUsd().setScale(CurrencyConverter.RATE_SCALE, RoundingMode.HALF_UP);
        if (BASE_CURRENCY.equals(normalised) && rate.compareTo(BigDecimal.ONE) != 0) {
            throw new CustomException("USD is the base currency; its rate to USD must be 1", HttpStatus.BAD_REQUEST);
        }
        Optional<CurrencyRateEntity> existing = currencyRateRepository.findById(normalised);
        CurrencyRateEntity currency = existing.orElseGet(() -> CurrencyRateEntity.builder().code(normalised).build());
        currency.setName(request.getName().trim());
        currency.setSymbol(request.getSymbol().trim());
        currency.setRateToUsd(rate);
        currency.setUpdatedAt(LocalDateTime.now(commerceClock));
        return new CurrencyUpsert(CurrencyDto.from(currencyRateRepository.save(currency)), existing.isEmpty());
    }

    private CurrencyRateEntity find(String code, String parameter) {
        String normalised = normaliseCode(code, parameter);
        return currencyRateRepository.findById(normalised)
                .orElseThrow(() -> new CustomException("Currency not found: " + normalised, HttpStatus.NOT_FOUND));
    }

    static String normaliseCode(String code, String name) {
        if (code == null || code.isBlank()) {
            throw new CustomException(name + " is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = code.trim();
        if (!CODE.matcher(trimmed).matches()) {
            throw new CustomException(name + " must be a 3-letter ISO 4217 code", HttpStatus.BAD_REQUEST);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    static BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new CustomException("amount is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = amount.trim();
        if (NEGATIVE_NUMBER.matcher(trimmed).matches()) {
            throw new CustomException("amount must not be negative", HttpStatus.BAD_REQUEST);
        }
        if (!NUMBER.matcher(trimmed).matches()) {
            throw new CustomException("amount must be a number", HttpStatus.BAD_REQUEST);
        }
        BigDecimal value = new BigDecimal(trimmed);
        if (value.stripTrailingZeros().scale() > MONEY_SCALE) {
            throw new CustomException("amount must have at most 2 decimals", HttpStatus.BAD_REQUEST);
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new CustomException("amount must not exceed " + MAX_AMOUNT, HttpStatus.BAD_REQUEST);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
