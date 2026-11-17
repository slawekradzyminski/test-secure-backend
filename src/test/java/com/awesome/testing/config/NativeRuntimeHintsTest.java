package com.awesome.testing.config;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.event.EventFeedDto;
import com.awesome.testing.dto.event.FeedEventDto;
import com.awesome.testing.dto.payment.PaymentResultDto;
import com.awesome.testing.dto.stock.AdjustStockRequestDto;
import com.awesome.testing.dto.stock.ConfirmReservationRequestDto;
import com.awesome.testing.dto.stock.CreateStockItemRequestDto;
import com.awesome.testing.dto.stock.LedgerMovementDto;
import com.awesome.testing.dto.stock.ReservationDto;
import com.awesome.testing.dto.stock.ReserveStockRequestDto;
import com.awesome.testing.dto.stock.ReservedLineDto;
import com.awesome.testing.dto.stock.StockLevelDto;
import com.awesome.testing.dto.stock.StockLevelsDto;
import com.awesome.testing.dto.stock.StockLevelsResultDto;
import com.awesome.testing.dto.stock.StockLineDto;
import com.awesome.testing.event.CommerceEvent;
import com.awesome.testing.internal.dto.InventoryExportDto;
import com.awesome.testing.internal.dto.InventoryExportItemDto;
import com.awesome.testing.internal.dto.OrderReferenceDto;
import com.awesome.testing.fakedata.ProductSeedDefinition;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.UserService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the native-image metadata that Spring cannot infer; each hint fixed a native failure. */
class NativeRuntimeHintsTest {

    private static final RuntimeHints HINTS = new RuntimeHints();

    @BeforeAll
    static void registerHints() {
        new NativeRuntimeHints.Registrar().registerHints(HINTS, NativeRuntimeHintsTest.class.getClassLoader());
    }

    @Test
    void registersResourcesLoadedOutsideSpringDefaults() {
        assertThat(RuntimeHintsPredicates.resource().forResource("graphql-preview/commerce.graphqls")).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.resource().forResource("bootstrap/products.json")).accepts(HINTS);
    }

    @Test
    void registersJacksonBindingForDtoAndSeedTypes() {
        assertThat(RuntimeHintsPredicates.reflection().onType(EmailDto.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(ProductSeedDefinition.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(CommerceEvent.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(PaymentResultDto.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(EventFeedDto.class)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(FeedEventDto.class)).accepts(HINTS);
    }

    @Test
    void registersJacksonBindingForWarehouseStockLedgerTypes() {
        for (Class<?> type : new Class<?>[] {ReserveStockRequestDto.class, ReservationDto.class,
                ReservedLineDto.class, StockLineDto.class, ConfirmReservationRequestDto.class,
                AdjustStockRequestDto.class, LedgerMovementDto.class, CreateStockItemRequestDto.class,
                StockLevelDto.class, StockLevelsDto.class, StockLevelsResultDto.class,
                InventoryExportDto.class, InventoryExportItemDto.class,
                OrderReferenceDto.class}) {
            assertThat(RuntimeHintsPredicates.reflection().onType(type)).as(type.getSimpleName()).accepts(HINTS);
        }
    }

    @Test
    void registersUuidArraysForHibernateMultiIdLoading() {
        assertThat(RuntimeHintsPredicates.reflection().onType(java.util.UUID[].class)).accepts(HINTS);
    }

    @Test
    void registersSecurityExpressionTargets() {
        assertThat(RuntimeHintsPredicates.reflection().onType(UserService.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection().onType(CustomPrincipal.class)
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
    }

    @Test
    void registersLibrariesThatLoadClassesByName() {
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(TypeReference.of("org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory"))
                .withMemberCategory(MemberCategory.INVOKE_PUBLIC_METHODS)).accepts(HINTS);
        assertThat(RuntimeHintsPredicates.reflection()
                .onType(TypeReference.of("org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle_impl"))
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).accepts(HINTS);
        assertThat(HINTS.reflection().typeHints()
                .anyMatch(hint -> hint.getType().getName().startsWith("com.github.benmanes.caffeine.cache.SS")))
                .isTrue();
        assertThat(HINTS.reflection().typeHints()
                .noneMatch(hint -> hint.getType().getName().endsWith("package-info"))).isTrue();
    }
}
