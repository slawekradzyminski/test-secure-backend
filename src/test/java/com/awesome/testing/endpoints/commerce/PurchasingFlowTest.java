package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderLineRequestDto;
import com.awesome.testing.dto.purchasing.PurchaseOrderRequestDto;
import com.awesome.testing.dto.purchasing.ReceiptLineDto;
import com.awesome.testing.dto.purchasing.ReceiptRequestDto;
import com.awesome.testing.dto.purchasing.ReorderSuggestionDto;
import com.awesome.testing.dto.purchasing.SupplierDto;
import com.awesome.testing.dto.purchasing.SupplierRequestDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.purchasing.PurchaseOrderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasingFlowTest extends AbstractCommerceFlowTest {

    private static final String SUPPLIERS = "/api/v1/admin/suppliers";
    private static final String POS = "/api/v1/admin/purchase-orders";

    @AfterEach
    void removePurchaseOrders() {
        commerceTestData.deleteAll();
    }

    @Test
    void purchaseOrderLifecycleBooksPartialAndFinalReceipts() {
        SupplierDto supplier = supplier("Nordic Test Supply", 7);
        ProductEntity product = product("10.00", 0);
        PurchaseOrderDto draft = createDraft(supplier, List.of(line(product, 10)));

        PurchaseOrderDto submitted = executePost(POS + "/" + draft.getId() + "/submit", null,
                getHeadersWith(adminToken), PurchaseOrderDto.class).getBody();
        PurchaseOrderDto partial = receive(draft, UUID.randomUUID(), product, 4, PurchaseOrderDto.class).getBody();
        ResponseEntity<ErrorDto> overReceipt = receive(draft, UUID.randomUUID(), product, 7, ErrorDto.class);
        PurchaseOrderDto complete = receive(draft, UUID.randomUUID(), product, 6, PurchaseOrderDto.class).getBody();
        ResponseEntity<ErrorDto> cancelReceived = executePost(POS + "/" + draft.getId() + "/cancel", null,
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(draft.getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(ChronoUnit.DAYS.between(submitted.getSubmittedAt(), submitted.getExpectedAt())).isEqualTo(7);
        assertThat(partial.getStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(partial.getLines().getFirst().getOutstandingQuantity()).isEqualTo(6);
        assertThat(overReceipt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(complete.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(complete.getReceivedAt()).isNotNull();
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(cancelReceived.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void repeatedReceiptRequestIdIsReplayedOnceAndConflictsWhenLinesDiffer() {
        ProductEntity product = product("10.00", 0);
        PurchaseOrderDto draft = createDraft(supplier("Replay Supply", 3), List.of(line(product, 10)));
        executePost(POS + "/" + draft.getId() + "/submit", null, getHeadersWith(adminToken), PurchaseOrderDto.class);
        UUID requestId = UUID.randomUUID();

        receive(draft, requestId, product, 3, PurchaseOrderDto.class);
        ResponseEntity<PurchaseOrderDto> replay = receive(draft, requestId, product, 3, PurchaseOrderDto.class);
        ResponseEntity<ErrorDto> changed = receive(draft, requestId, product, 4, ErrorDto.class);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().getLines().getFirst().getReceivedQuantity()).isEqualTo(3);
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void draftsAreEditableButNeedLinesToSubmitAndCannotBeReceivedIntoBeforeSubmission() {
        SupplierDto supplier = supplier("Draft Supply", 0);
        ProductEntity product = product("10.00", 0);
        PurchaseOrderDto empty = createDraft(supplier, List.of());

        ResponseEntity<ErrorDto> submitEmpty = executePost(POS + "/" + empty.getId() + "/submit", null,
                getHeadersWith(adminToken), ErrorDto.class);
        ResponseEntity<ErrorDto> receiveDraft = receive(empty, UUID.randomUUID(), product, 1, ErrorDto.class);
        ResponseEntity<PurchaseOrderDto> edited = executePut(POS + "/" + empty.getId(),
                new PurchaseOrderRequestDto(supplier.getId(), "now with lines", List.of(line(product, 5))),
                getHeadersWith(adminToken), PurchaseOrderDto.class);
        ResponseEntity<ErrorDto> duplicateLines = executePost(POS,
                new PurchaseOrderRequestDto(supplier.getId(), null, List.of(line(product, 1), line(product, 2))),
                getHeadersWith(adminToken), ErrorDto.class);

        assertThat(submitEmpty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(receiveDraft.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(edited.getBody().getLines()).hasSize(1);
        assertThat(edited.getBody().getTotalCost()).isEqualByComparingTo("40.00");
        assertThat(duplicateLines.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void supplierNamesAreUniqueIgnoringCase() {
        supplier("Unique Supply", 5);

        ResponseEntity<ErrorDto> duplicate = executePost(SUPPLIERS,
                new SupplierRequestDto("unique supply", "x@supply.test", 5), getHeadersWith(adminToken), ErrorDto.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reorderSuggestionsCountIncomingUnitsFromOpenPurchaseOrders() {
        ProductEntity covered = product("10.00", 1);
        ProductEntity uncovered = product("10.00", 2);
        product("10.00", 50);
        PurchaseOrderDto draft = createDraft(supplier("Reorder Supply", 1), List.of(line(covered, 10)));
        executePost(POS + "/" + draft.getId() + "/submit", null, getHeadersWith(adminToken), PurchaseOrderDto.class);

        List<ReorderSuggestionDto> suggestions = executeGet(
                "/api/v1/admin/inventory/reorder-suggestions?lowStockThreshold=5", getHeadersWith(adminToken),
                new ParameterizedTypeReference<List<ReorderSuggestionDto>>() {}).getBody();

        assertThat(suggestions).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.getProductId()).isEqualTo(uncovered.getId());
            assertThat(suggestion.getSuggestedQuantity()).isEqualTo(18);
        });
    }

    private SupplierDto supplier(String name, int leadTime) {
        ResponseEntity<SupplierDto> response = executePost(SUPPLIERS,
                new SupplierRequestDto(name, "buyer@supply.test", leadTime), getHeadersWith(adminToken), SupplierDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private PurchaseOrderLineRequestDto line(ProductEntity product, int quantity) {
        return new PurchaseOrderLineRequestDto(product.getId(), quantity, new BigDecimal("8.00"));
    }

    private PurchaseOrderDto createDraft(SupplierDto supplier, List<PurchaseOrderLineRequestDto> lines) {
        ResponseEntity<PurchaseOrderDto> response = executePost(POS,
                new PurchaseOrderRequestDto(supplier.getId(), null, lines), getHeadersWith(adminToken), PurchaseOrderDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private <T> ResponseEntity<T> receive(PurchaseOrderDto po, UUID requestId, ProductEntity product, int quantity, Class<T> type) {
        return executePost(POS + "/" + po.getId() + "/receipts",
                new ReceiptRequestDto(requestId, List.of(new ReceiptLineDto(product.getId(), quantity))),
                getHeadersWith(adminToken), type);
    }
}
