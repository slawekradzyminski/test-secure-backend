package com.awesome.testing.service.tender;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.entity.giftcard.GiftCardEntity;
import com.awesome.testing.entity.tender.CartTenderEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.tender.CartTenderRepository;
import com.awesome.testing.service.giftcard.GiftCardService;
import com.awesome.testing.service.loyalty.LoyaltyPoints;
import com.awesome.testing.service.loyalty.LoyaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The gift card and loyalty points a shopper applies to the cart, like a
 * coupon: validated when applied, shown in the cart summary, validated again
 * and consumed at checkout.
 */
@Service
@RequiredArgsConstructor
public class CartTenderService {

    private final CartItemRepository cartItemRepository;
    private final CartTenderRepository cartTenderRepository;
    private final GiftCardService giftCardService;
    private final LoyaltyService loyaltyService;

    /** Applies a gift card, replacing any previous one. */
    @Transactional
    public void applyGiftCard(String username, String rawCode) {
        requireItems(username);
        GiftCardEntity card = giftCardService.find(rawCode);
        giftCardService.evaluate(card).ifPresent(failure -> {
            throw new CustomException(failure.message(), failure.status());
        });
        CartTenderEntity selection = selectionOrNew(username);
        selection.setGiftCardCode(card.getCode());
        cartTenderRepository.save(selection);
    }

    @Transactional
    public void removeGiftCard(String username) {
        cartTenderRepository.findById(username).ifPresent(selection -> {
            selection.setGiftCardCode(null);
            saveOrDelete(selection);
        });
    }

    /** Applies loyalty points, replacing any previous amount. */
    @Transactional
    public void applyPoints(String username, long points) {
        requireItems(username);
        if (!LoyaltyPoints.isRedeemable(points)) {
            throw new CustomException("Points must be a whole multiple of 100", HttpStatus.BAD_REQUEST);
        }
        if (loyaltyService.balanceOf(username) < points) {
            throw new CustomException("Insufficient loyalty points", HttpStatus.CONFLICT);
        }
        CartTenderEntity selection = selectionOrNew(username);
        selection.setPoints(points);
        cartTenderRepository.save(selection);
    }

    @Transactional
    public void removePoints(String username) {
        cartTenderRepository.findById(username).ifPresent(selection -> {
            selection.setPoints(null);
            saveOrDelete(selection);
        });
    }

    @Transactional(readOnly = true)
    public Optional<CartTenderEntity> selection(String username) {
        return cartTenderRepository.findById(username).filter(selection -> !selection.isEmpty());
    }

    @Transactional
    public void clear(String username) {
        cartTenderRepository.deleteByUsername(username);
    }

    private void requireItems(String username) {
        if (cartItemRepository.findByUsername(username).isEmpty()) {
            throw new CustomException("Cart is empty", HttpStatus.BAD_REQUEST);
        }
    }

    private CartTenderEntity selectionOrNew(String username) {
        return cartTenderRepository.findById(username)
                .orElseGet(() -> CartTenderEntity.builder().username(username).build());
    }

    private void saveOrDelete(CartTenderEntity selection) {
        if (selection.isEmpty()) {
            cartTenderRepository.delete(selection);
        } else {
            cartTenderRepository.save(selection);
        }
    }
}
