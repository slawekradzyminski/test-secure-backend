package com.awesome.testing.service.tender;

import com.awesome.testing.dto.cart.CartDto;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.service.checkout.CartSummaryContext;
import com.awesome.testing.service.giftcard.GiftCardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderCartSummaryContributorTest {

    private static final String USER = "buyer";
    private static final String CODE = "GC-ABCD-EFGH-JKMN";

    @Mock
    private CartTenderService cartTenderService;
    @Mock
    private GiftCardService giftCardService;

    @InjectMocks
    private TenderCartSummaryContributor contributor;

    @Test
    void cartsWithoutTendersKeepTheirShape() {
        when(cartTenderService.selection(USER)).thenReturn(Optional.empty());
        CartDto cart = new CartDto();

        contributor.contribute(context(cart, "50.00", "0"));

        assertThat(cart).isEqualTo(new CartDto());
        verifyNoInteractions(giftCardService);
    }

    @Test
    void previewsGiftCardAndPointsAfterTheDiscount() {
        GiftCardEntity card = GiftCardEntity.builder().code(CODE).build();
        when(cartTenderService.selection(USER)).thenReturn(Optional.of(
                CartTenderEntity.builder().username(USER).giftCardCode(CODE).points(10_000L).build()));
        when(giftCardService.findByCode(CODE)).thenReturn(Optional.of(card));
        when(giftCardService.usableBalance(card)).thenReturn(new BigDecimal("60.00"));
        CartDto cart = new CartDto();

        contributor.contribute(context(cart, "100.00", "10.00"));

        assertThat(cart.getGiftCardCode()).isEqualTo(CODE);
        assertThat(cart.getGiftCardAmount()).isEqualByComparingTo("60.00");
        assertThat(cart.getPointsRedeemed()).isEqualTo(3_000L);
        assertThat(cart.getPointsAmount()).isEqualByComparingTo("30.00");
        assertThat(cart.getAmountDue()).isEqualByComparingTo("0.00");
    }

    @Test
    void aDeletedCardPreviewsAsZeroWithoutPointFields() {
        when(cartTenderService.selection(USER)).thenReturn(Optional.of(
                CartTenderEntity.builder().username(USER).giftCardCode(CODE).build()));
        when(giftCardService.findByCode(CODE)).thenReturn(Optional.empty());
        CartDto cart = new CartDto();

        contributor.contribute(context(cart, "20.00", "0"));

        assertThat(cart.getGiftCardAmount()).isEqualByComparingTo("0");
        assertThat(cart.getPointsRedeemed()).isNull();
        assertThat(cart.getPointsAmount()).isNull();
        assertThat(cart.getAmountDue()).isEqualByComparingTo("20.00");
    }

    @Test
    void pointsAloneLeaveTheGiftCardFieldsOut() {
        when(cartTenderService.selection(USER)).thenReturn(Optional.of(
                CartTenderEntity.builder().username(USER).points(500L).build()));
        CartDto cart = new CartDto();

        contributor.contribute(context(cart, "20.00", "0"));

        assertThat(cart.getGiftCardCode()).isNull();
        assertThat(cart.getGiftCardAmount()).isNull();
        assertThat(cart.getPointsRedeemed()).isEqualTo(500L);
        assertThat(cart.getAmountDue()).isEqualByComparingTo("15.00");
        verifyNoInteractions(giftCardService);
    }

    private static CartSummaryContext context(CartDto cart, String subtotal, String discount) {
        return new CartSummaryContext(USER, List.of(), new BigDecimal(subtotal), new BigDecimal(discount), cart);
    }
}
