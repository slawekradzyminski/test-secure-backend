package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.coupon.ApplyCouponDto;
import com.awesome.testing.dto.coupon.CouponRequestDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.tax.TaxPreviewDto;
import com.awesome.testing.dto.tax.VatRateDto;
import com.awesome.testing.dto.tax.VatRateRequestDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.coupon.CouponType;
import com.awesome.testing.entity.tax.VatRateEntity;
import com.awesome.testing.repository.tax.VatRateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

class VatFlowTest extends AbstractCommerceFlowTest {

    private static final String VAT_RATES = "/api/v1/vat-rates";
    private static final String ADMIN_VAT_RATES = "/api/v1/admin/vat-rates";
    private static final String TAX_PREVIEW = "/api/v1/cart/tax-preview";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> LEGACY_CART = Set.of("username", "items", "totalPrice", "totalItems");
    private static final Set<String> LEGACY_ORDER = Set.of(
            "id", "username", "items", "totalAmount", "status", "shippingAddress", "createdAt", "updatedAt");
    private static final Set<String> TAX_FIELDS = Set.of("taxCountry", "taxRate", "taxAmount", "netAmount");

    @Autowired
    private VatRateRepository vatRateRepository;

    @BeforeEach
    void seedRates() {
        vatRateRepository.deleteAll();
        vatRateRepository.saveAll(List.of(rate("PL", "Poland", "23.00"), rate("HU", "Hungary", "27.00"),
                rate("GB", "United Kingdom", "20.00"), rate("US", "United States", "0.00")));
    }

    @AfterEach
    void removeRates() {
        vatRateRepository.deleteAll();
    }

    // ---- orders ----

    @Test
    void orderRecordsTheVatContainedInItsTotalIdenticallyOnPostGetAndList() {
        addToCart(clientToken, product("61.50", 5), 2);

        ResponseEntity<String> created = executePost(ORDERS_ENDPOINT, address("Polska"), getHeadersWith(clientToken), String.class);
        JsonNode posted = body(created);
        long id = posted.get("id").asLong();
        JsonNode fetched = body(executeGet(ORDERS_ENDPOINT + "/" + id, getHeadersWith(clientToken), String.class));
        JsonNode listed = body(executeGet(ORDERS_ENDPOINT, getHeadersWith(clientToken), String.class)).get("content").get(0);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(posted.get("totalAmount").decimalValue()).isEqualByComparingTo("123.00");
        assertThat(posted.get("taxCountry").asString()).isEqualTo("PL");
        assertThat(posted.get("taxRate").decimalValue()).isEqualByComparingTo("23.00");
        assertThat(posted.get("taxAmount").decimalValue()).isEqualByComparingTo("23.00");
        assertThat(posted.get("netAmount").decimalValue()).isEqualByComparingTo("100.00");
        for (String field : TAX_FIELDS) {
            assertThat(fetched.get(field)).as(field).isEqualTo(posted.get(field));
            assertThat(listed.get(field)).as(field).isEqualTo(posted.get(field));
        }
        assertThat(posted.get("items").get(0).propertyNames()).noneMatch(TAX_FIELDS::contains);
    }

    @Test
    void hungarianVatAppliesToTheTotalAfterTheCoupon() {
        executePost("/api/v1/admin/coupons", CouponRequestDto.builder().code("VAT20").type(CouponType.FIXED_AMOUNT)
                .value(new BigDecimal("20.00")).build(), getHeadersWith(adminToken), String.class);
        addToCart(clientToken, product("147.00", 5), 1);
        executePut("/api/v1/cart/coupon", new ApplyCouponDto("VAT20"), getHeadersWith(clientToken), String.class);

        OrderDto order = executePost(ORDERS_ENDPOINT, address("hungary"), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(order.getSubtotalAmount()).isEqualByComparingTo("147.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("127.00");
        assertThat(order.getTaxCountry()).isEqualTo("HU");
        assertThat(order.getTaxRate()).isEqualByComparingTo("27");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("27.00");
        assertThat(order.getNetAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void zeroRateCountriesShowZeroVat() {
        addToCart(clientToken, product("10.00", 5), 1);

        OrderDto order = executePost(ORDERS_ENDPOINT, address("USA"), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(order.getTaxCountry()).isEqualTo("US");
        assertThat(order.getTaxRate()).isEqualByComparingTo("0");
        assertThat(order.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(order.getNetAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    void ordersToUnknownCountriesAndTheCartKeepTheirLegacyJson() {
        addToCart(clientToken, product("12.50", 5), 2);

        JsonNode cart = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));
        ResponseEntity<String> created = executePost(ORDERS_ENDPOINT, address("Atlantis"), getHeadersWith(clientToken), String.class);
        JsonNode order = body(created);
        JsonNode fetched = body(executeGet(ORDERS_ENDPOINT + "/" + order.get("id").asLong(), getHeadersWith(clientToken), String.class));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(fields(cart)).isEqualTo(LEGACY_CART);
        assertThat(fields(order)).isEqualTo(LEGACY_ORDER);
        assertThat(fields(fetched)).isEqualTo(LEGACY_ORDER);
        assertThat(order.get("totalAmount").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void cartJsonIsUnchangedForAShopperInATaxedCountry() {
        addToCart(clientToken, product("12.50", 5), 2);

        JsonNode cart = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));
        executeGet(TAX_PREVIEW + "?country=PL", getHeadersWith(clientToken), String.class);
        JsonNode after = body(executeGet(CART_ENDPOINT, getHeadersWith(clientToken), String.class));

        assertThat(fields(cart)).isEqualTo(LEGACY_CART);
        assertThat(after).isEqualTo(cart);
    }

    @Test
    void adminRateChangesAffectOnlyNewOrders() {
        addToCart(clientToken, product("61.50", 5), 2);
        OrderDto before = executePost(ORDERS_ENDPOINT, address("PL"), getHeadersWith(clientToken), OrderDto.class).getBody();

        ResponseEntity<VatRateDto> updated = executePut(ADMIN_VAT_RATES + "/pl", request("Poland", "8"),
                getHeadersWith(adminToken), VatRateDto.class);
        addToCart(clientToken, product("108.00", 5), 1);
        OrderDto after = executePost(ORDERS_ENDPOINT, address("Poland"), getHeadersWith(clientToken), OrderDto.class).getBody();
        OrderDto reread = executeGet(ORDERS_ENDPOINT + "/" + before.getId(), getHeadersWith(clientToken), OrderDto.class).getBody();

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reread.getTaxRate()).isEqualByComparingTo("23.00");
        assertThat(reread.getTaxAmount()).isEqualByComparingTo("23.00");
        assertThat(after.getTaxRate()).isEqualByComparingTo("8.00");
        assertThat(after.getTaxAmount()).isEqualByComparingTo("8.00");
        assertThat(after.getNetAmount()).isEqualByComparingTo("100.00");
    }

    // ---- GET /vat-rates ----

    @Test
    void listsRatesForAuthenticatedUsers() {
        ResponseEntity<List<VatRateDto>> response = executeGet(VAT_RATES, getHeadersWith(clientToken),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(VatRateDto::getCountryCode).containsExactly("GB", "HU", "PL", "US");
        assertThat(response.getBody().get(2).getCountryName()).isEqualTo("Poland");
        assertThat(response.getBody().get(2).getRate()).isEqualByComparingTo("23.00");
    }

    @Test
    void readingRatesRequiresAuthentication() {
        assertThat(executeGet(VAT_RATES, getJsonOnlyHeaders(), ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(executeGet(VAT_RATES + "/PL", getJsonOnlyHeaders(), ErrorDto.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getsARateByCodeOrName() {
        ResponseEntity<VatRateDto> byName = executeGet(VAT_RATES + "/United Kingdom", getHeadersWith(clientToken), VatRateDto.class);
        ResponseEntity<VatRateDto> byAlias = executeGet(VAT_RATES + "/uk", getHeadersWith(clientToken), VatRateDto.class);
        ResponseEntity<VatRateDto> byAlpha3 = executeGet(VAT_RATES + "/HUN", getHeadersWith(clientToken), VatRateDto.class);

        assertThat(byName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byName.getBody().getCountryCode()).isEqualTo("GB");
        assertThat(byAlias.getBody().getCountryCode()).isEqualTo("GB");
        assertThat(byAlpha3.getBody().getRate()).isEqualByComparingTo("27.00");
    }

    @Test
    void unknownCountryRateIsNotFound() {
        ResponseEntity<ErrorDto> response = executeGet(VAT_RATES + "/Atlantis", getHeadersWith(clientToken), ErrorDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("VAT rate not found for country: Atlantis");
    }

    // ---- PUT /admin/vat-rates ----

    @Test
    void adminCreatesARate() {
        ResponseEntity<VatRateDto> response = executePut(ADMIN_VAT_RATES + "/jp", request("Japan", "10.00"),
                getHeadersWith(adminToken), VatRateDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getCountryCode()).isEqualTo("JP");
        assertThat(response.getBody().getRate()).isEqualByComparingTo("10.00");
        assertThat(response.getBody().getUpdatedAt()).isNotNull();
        assertThat(executeGet(VAT_RATES + "/JP", getHeadersWith(clientToken), VatRateDto.class).getBody().getCountryName())
                .isEqualTo("Japan");
    }

    @Test
    void adminRateValidationFailsWithFieldErrors() {
        ResponseEntity<Map<String, String>> tooHigh = executePut(ADMIN_VAT_RATES + "/PL", request("Poland", "50.01"),
                getHeadersWith(adminToken), new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, String>> tooPrecise = executePut(ADMIN_VAT_RATES + "/PL", request(" ", "23.005"),
                getHeadersWith(adminToken), new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, String>> negative = executePut(ADMIN_VAT_RATES + "/PL", request("Poland", "-1"),
                getHeadersWith(adminToken), new ParameterizedTypeReference<>() {});
        ResponseEntity<Map<String, String>> missing = executePut(ADMIN_VAT_RATES + "/PL", new VatRateRequestDto(),
                getHeadersWith(adminToken), new ParameterizedTypeReference<>() {});

        assertThat(tooHigh.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooHigh.getBody()).containsEntry("rate", "Rate must be between 0 and 50");
        assertThat(tooPrecise.getBody()).containsEntry("rate", "Rate must have at most 2 decimals")
                .containsEntry("countryName", "Country name is required");
        assertThat(negative.getBody()).containsEntry("rate", "Rate must be between 0 and 50");
        assertThat(missing.getBody()).containsKeys("rate", "countryName");
        assertThat(vatRateRepository.findById("PL").orElseThrow().getRate()).isEqualByComparingTo("23.00");
    }

    @Test
    void adminRateRejectsMalformedCountryCodes() {
        ResponseEntity<ErrorDto> response = executePut(ADMIN_VAT_RATES + "/POL", request("Poland", "23"),
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Country code must be an ISO 3166-1 alpha-2 code");
    }

    @Test
    void onlyAdminsChangeRates() {
        ResponseEntity<ErrorDto> client = executePut(ADMIN_VAT_RATES + "/PL", request("Poland", "5"),
                getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> anonymous = executePut(ADMIN_VAT_RATES + "/PL", request("Poland", "5"),
                getJsonOnlyHeaders(), ErrorDto.class);

        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(vatRateRepository.findById("PL").orElseThrow().getRate()).isEqualByComparingTo("23.00");
    }

    // ---- GET /cart/tax-preview ----

    @Test
    void previewsTheCartVat() {
        addToCart(clientToken, product("61.50", 5), 2);

        ResponseEntity<TaxPreviewDto> response = executeGet(TAX_PREVIEW + "?country=Poland", getHeadersWith(clientToken),
                TaxPreviewDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(TaxPreviewDto.builder().country("PL").rate(new BigDecimal("23.00"))
                .taxAmount(new BigDecimal("23.00")).netAmount(new BigDecimal("100.00"))
                .totalAmount(new BigDecimal("123.00")).build());
    }

    @Test
    void previewOfAnEmptyCartIsZero() {
        TaxPreviewDto preview = executeGet(TAX_PREVIEW + "?country=HU", getHeadersWith(clientToken), TaxPreviewDto.class).getBody();

        assertThat(preview.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(preview.getTaxAmount()).isEqualByComparingTo("0");
    }

    @Test
    void previewNeedsAKnownCountryAndAUser() {
        ResponseEntity<ErrorDto> missing = executeGet(TAX_PREVIEW, getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> unknown = executeGet(TAX_PREVIEW + "?country=Atlantis", getHeadersWith(clientToken), ErrorDto.class);
        ResponseEntity<ErrorDto> anonymous = executeGet(TAX_PREVIEW + "?country=PL", getJsonOnlyHeaders(), ErrorDto.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().getMessage()).isEqualTo("country is required");
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static AddressDto address(String country) {
        AddressDto address = getRandomAddress();
        address.setCountry(country);
        return address;
    }

    private static VatRateRequestDto request(String name, String rate) {
        return VatRateRequestDto.builder().countryName(name).rate(new BigDecimal(rate)).build();
    }

    private static VatRateEntity rate(String code, String name, String rate) {
        return VatRateEntity.builder().countryCode(code).countryName(name).rate(new BigDecimal(rate))
                .updatedAt(LocalDateTime.now()).build();
    }

    private static JsonNode body(ResponseEntity<String> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as(response.getBody()).isTrue();
        return JSON.readTree(response.getBody());
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
