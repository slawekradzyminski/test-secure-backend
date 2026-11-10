package com.awesome.testing.service.tender;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.repository.tender.CartTenderRepository;
import com.awesome.testing.service.checkout.CheckoutContext;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderCheckoutContributorTest {

    private static final String USER = "buyer";
    private static final String CODE = "GC-ABCD-EFGH-JKMN";

    @Mock
    private CartTenderRepository cartTenderRepository;
    @Mock
    private GiftCardService giftCardService;
    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private TenderCheckoutContributor contributor;

    @Test
    void ordersWithoutTendersAreLeftAlone() {
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.empty());
        OrderEntity order = order("50.00");

        contributor.contribute(context(order));

        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getPointsRedeemed()).isNull();
        verifyNoInteractions(giftCardService, loyaltyService);
    }

    @Test
    void giftCardThenPointsPayAndTheSelectionIsCleared() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).balance(new BigDecimal("10.00")).build();
        CartTenderEntity selection = CartTenderEntity.builder().username(USER).giftCardCode(CODE).points(1_000L).build();
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(selection));
        when(giftCardService.lockForCheckout(CODE)).thenReturn(card);
        OrderEntity order = order("29.00");

        contributor.contribute(context(order));

        verify(giftCardService).debit(card, new BigDecimal("10.00"));
        verify(loyaltyService).debitForCheckout(USER, 1_000L);
        assertThat(order.getGiftCardCode()).isEqualTo(CODE);
        assertThat(order.getGiftCardAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getPointsRedeemed()).isEqualTo(1_000L);
        assertThat(order.getPointsAmount()).isEqualByComparingTo("10.00");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("29.00");
        verify(cartTenderRepository).delete(selection);
    }

    @Test
    void tendersThatAreNotNeededAreNotTaken() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).balance(new BigDecimal("80.00")).build();
        CartTenderEntity selection = CartTenderEntity.builder().username(USER).giftCardCode(CODE).points(500L).build();
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(selection));
        when(giftCardService.lockForCheckout(CODE)).thenReturn(card);
        OrderEntity order = order("50.00");

        contributor.contribute(context(order));

        verify(giftCardService).debit(card, new BigDecimal("50.00"));
        verify(loyaltyService, never()).debitForCheckout(anyString(), anyLong());
        assertThat(order.getPointsRedeemed()).isNull();
        assertThat(order.getPointsAmount()).isNull();
        verify(cartTenderRepository).delete(selection);
    }

    @Test
    void zeroTotalsRecordNoTender() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).balance(new BigDecimal("80.00")).build();
        CartTenderEntity selection = CartTenderEntity.builder().username(USER).giftCardCode(CODE).build();
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(selection));
        when(giftCardService.lockForCheckout(CODE)).thenReturn(card);
        OrderEntity order = order("0.00");

        contributor.contribute(context(order));

        verify(giftCardService, never()).debit(any(), any());
        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getGiftCardAmount()).isNull();
        verify(cartTenderRepository).delete(selection);
    }

    @Test
    void pointsAloneArePossible() {
        CartTenderEntity selection = CartTenderEntity.builder().username(USER).points(300L).build();
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(selection));
        OrderEntity order = order("12.34");

        contributor.contribute(context(order));

        verify(giftCardService, never()).lockForCheckout(anyString());
        verify(loyaltyService).debitForCheckout(USER, 300L);
        assertThat(order.getGiftCardCode()).isNull();
        assertThat(order.getPointsAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    void anInvalidTenderAbortsCheckoutAndKeepsTheSelection() {
        CartTenderEntity selection = CartTenderEntity.builder().username(USER).giftCardCode(CODE).points(300L).build();
        when(cartTenderRepository.findByUsernameForUpdate(USER)).thenReturn(Optional.of(selection));
        when(giftCardService.lockForCheckout(CODE)).thenReturn(GiftCardEntity.builder().code(CODE).balance(BigDecimal.ONE).build());
        doThrow(new CustomException("no points", HttpStatus.CONFLICT)).when(loyaltyService).debitForCheckout(USER, 300L);

        assertThatThrownBy(() -> contributor.contribute(context(order("20.00")))).isInstanceOf(CustomException.class);
        verify(cartTenderRepository, never()).delete(any());
    }

    private static OrderEntity order(String total) {
        return OrderEntity.builder().username(USER).totalAmount(new BigDecimal(total)).build();
    }

    private static CheckoutContext context(OrderEntity order) {
        return new CheckoutContext(USER, new AddressDto(), List.of(), order);
    }
}
