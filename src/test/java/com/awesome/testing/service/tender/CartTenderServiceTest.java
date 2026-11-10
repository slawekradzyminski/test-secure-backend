package com.awesome.testing.service.tender;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.tender.CartTenderRepository;
import com.awesome.testing.service.giftcard.GiftCardFailure;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartTenderServiceTest {

    private static final String USER = "buyer";
    private static final String CODE = "GC-ABCD-EFGH-JKMN";

    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private CartTenderRepository cartTenderRepository;
    @Mock
    private GiftCardService giftCardService;
    @Mock
    private LoyaltyService loyaltyService;

    @InjectMocks
    private CartTenderService service;

    @Test
    void applyingAGiftCardStoresItsNormalizedCode() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).build();
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of(new CartItemEntity()));
        when(giftCardService.find("gc-abcd-efgh-jkmn")).thenReturn(card);
        when(giftCardService.evaluate(card)).thenReturn(Optional.empty());
        CartTenderEntity existing = CartTenderEntity.builder().username(USER).points(200L).build();
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.of(existing));

        service.applyGiftCard(USER, "gc-abcd-efgh-jkmn");

        assertThat(existing.getGiftCardCode()).isEqualTo(CODE);
        assertThat(existing.getPoints()).isEqualTo(200L);
        verify(cartTenderRepository).save(existing);
    }

    @Test
    void applyingToANewCartCreatesTheSelection() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).build();
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of(new CartItemEntity()));
        when(giftCardService.find(CODE)).thenReturn(card);
        when(giftCardService.evaluate(card)).thenReturn(Optional.empty());
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.empty());

        service.applyGiftCard(USER, CODE);

        ArgumentCaptor<CartTenderEntity> saved = ArgumentCaptor.forClass(CartTenderEntity.class);
        verify(cartTenderRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo(USER);
        assertThat(saved.getValue().getGiftCardCode()).isEqualTo(CODE);
        assertThat(saved.getValue().getPoints()).isNull();
    }

    @Test
    void unusableCardsAreRejectedWithTheirReason() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).build();
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of(new CartItemEntity()));
        when(giftCardService.find(CODE)).thenReturn(card);
        when(giftCardService.evaluate(card)).thenReturn(Optional.of(new GiftCardFailure(HttpStatus.CONFLICT, "Gift card has no remaining balance")));

        assertThatThrownBy(() -> service.applyGiftCard(USER, CODE))
                .isInstanceOf(CustomException.class)
                .hasMessage("Gift card has no remaining balance")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(cartTenderRepository, never()).save(any());
    }

    @Test
    void emptyCartsCannotTakeTenders() {
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyGiftCard(USER, CODE))
                .isInstanceOf(CustomException.class).hasMessage("Cart is empty")
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.applyPoints(USER, 100))
                .isInstanceOf(CustomException.class).hasMessage("Cart is empty");
        verifyNoInteractions(giftCardService, loyaltyService);
    }

    @Test
    void pointsMustBeAStepAndCoveredByTheBalance() {
        when(cartItemRepository.findByUsername(USER)).thenReturn(List.of(new CartItemEntity()));
        when(loyaltyService.balanceOf(USER)).thenReturn(500L);
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyPoints(USER, 250))
                .isInstanceOf(CustomException.class).hasMessage("Points must be a whole multiple of 100")
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.applyPoints(USER, 600))
                .isInstanceOf(CustomException.class).hasMessage("Insufficient loyalty points")
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        service.applyPoints(USER, 500);

        ArgumentCaptor<CartTenderEntity> saved = ArgumentCaptor.forClass(CartTenderEntity.class);
        verify(cartTenderRepository).save(saved.capture());
        assertThat(saved.getValue().getPoints()).isEqualTo(500L);
    }

    @Test
    void removingOneTenderKeepsTheOther() {
        CartTenderEntity both = CartTenderEntity.builder().username(USER).giftCardCode(CODE).points(100L).build();
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.of(both));

        service.removeGiftCard(USER);

        assertThat(both.getGiftCardCode()).isNull();
        verify(cartTenderRepository).save(both);
        verify(cartTenderRepository, never()).delete(any());
    }

    @Test
    void removingTheLastTenderDeletesTheSelection() {
        CartTenderEntity points = CartTenderEntity.builder().username(USER).points(100L).build();
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.of(points));

        service.removePoints(USER);

        assertThat(points.getPoints()).isNull();
        verify(cartTenderRepository).delete(points);
        verify(cartTenderRepository, never()).save(any());
    }

    @Test
    void removingFromAnEmptySelectionIsANoOp() {
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.empty());

        service.removeGiftCard(USER);
        service.removePoints(USER);

        verify(cartTenderRepository, never()).save(any());
        verify(cartTenderRepository, never()).delete(any());
    }

    @Test
    void selectionIgnoresEmptyRowsAndClearDeletes() {
        when(cartTenderRepository.findById(USER)).thenReturn(Optional.of(CartTenderEntity.builder().username(USER).build()));
        when(cartTenderRepository.findById("other")).thenReturn(Optional.of(CartTenderEntity.builder().username("other").points(100L).build()));

        assertThat(service.selection(USER)).isEmpty();
        assertThat(service.selection("other")).isPresent();
        service.clear(USER);
        verify(cartTenderRepository).deleteByUsername(USER);
    }
}
