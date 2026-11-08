package com.awesome.testing.service.wishlist;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.wishlist.WishlistItemDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.wishlist.WishlistItemEntity;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.wishlist.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WishlistService {

    public static final int MAX_ITEMS = 50;

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<WishlistItemDto> list(String username) {
        return wishlistItemRepository.findByUsernameOrderByCreatedAtDescIdDesc(username).stream()
                .map(WishlistService::toDto)
                .toList();
    }

    /** Adds or updates an entry. Returns true when a new entry was created. */
    @Transactional
    public Upsert upsert(String username, Long productId, boolean notifyWhenAvailable) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        Optional<WishlistItemEntity> existing = wishlistItemRepository.findByUsernameAndProductId(username, productId);
        if (existing.isPresent()) {
            existing.get().setNotifyWhenAvailable(notifyWhenAvailable);
            return new Upsert(toDto(wishlistItemRepository.save(existing.get())), false);
        }
        if (wishlistItemRepository.countByUsername(username) >= MAX_ITEMS) {
            throw new CustomException("Wishlist cannot contain more than " + MAX_ITEMS + " products", HttpStatus.CONFLICT);
        }
        WishlistItemEntity created = wishlistItemRepository.saveAndFlush(WishlistItemEntity.builder()
                .username(username)
                .product(product)
                .notifyWhenAvailable(notifyWhenAvailable)
                .build());
        return new Upsert(toDto(created), true);
    }

    @Transactional
    public void remove(String username, Long productId) {
        WishlistItemEntity item = wishlistItemRepository.findByUsernameAndProductId(username, productId)
                .orElseThrow(() -> new CustomException("Product is not on the wishlist", HttpStatus.NOT_FOUND));
        wishlistItemRepository.delete(item);
    }

    private static WishlistItemDto toDto(WishlistItemEntity item) {
        ProductEntity product = item.getProduct();
        return WishlistItemDto.builder()
                .productId(product.getId())
                .productName(product.getName())
                .price(product.getPrice())
                .imageUrl(product.getImageUrl())
                .stockQuantity(product.getStockQuantity())
                .stockStatus(product.getStockQuantity() == 0 ? StockStatus.OUT_OF_STOCK : StockStatus.IN_STOCK)
                .notifyWhenAvailable(item.getNotifyWhenAvailable())
                .lastNotifiedAt(item.getLastNotifiedAt())
                .addedAt(item.getCreatedAt())
                .build();
    }

    public record Upsert(WishlistItemDto item, boolean created) {
    }
}
