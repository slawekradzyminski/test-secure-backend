package com.awesome.testing.service.review;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.ProductNotFoundException;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.review.ModerationDecision;
import com.awesome.testing.dto.review.ModerationRequestDto;
import com.awesome.testing.dto.review.MyReviewDto;
import com.awesome.testing.dto.review.ReviewDto;
import com.awesome.testing.dto.review.ReviewRequestDto;
import com.awesome.testing.dto.review.ReviewSummaryDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ProductReviewEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.review.ProductReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    static final String NOT_ELIGIBLE = "Only customers who received this product can review it";
    static final String ALREADY_REVIEWED = "You have already reviewed this product";

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<ReviewDto> approved(Long productId, Pageable pageable) {
        requireProduct(productId);
        return reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable)
                .map(review -> toDto(review, false));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto summary(Long productId) {
        requireProduct(productId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            distribution.put(rating, 0L);
        }
        for (Object[] row : reviewRepository.countByRating(productId, ReviewStatus.APPROVED)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return summarize(productId, distribution);
    }

    static ReviewSummaryDto summarize(Long productId, Map<Integer, Long> distribution) {
        long count = distribution.values().stream().mapToLong(Long::longValue).sum();
        long weighted = distribution.entrySet().stream().mapToLong(e -> e.getKey() * e.getValue()).sum();
        BigDecimal average = count == 0
                ? null
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        return ReviewSummaryDto.builder()
                .productId(productId)
                .averageRating(average)
                .reviewCount(count)
                .distribution(distribution)
                .build();
    }

    @Transactional(readOnly = true)
    public MyReviewDto mine(String username, Long productId) {
        requireProduct(productId);
        Optional<ProductReviewEntity> review = reviewRepository.findByProductIdAndUsername(productId, username);
        if (review.isPresent()) {
            return MyReviewDto.builder().canReview(false).reason(ALREADY_REVIEWED)
                    .review(toDto(review.get(), true)).build();
        }
        boolean eligible = hasReceived(username, productId);
        return MyReviewDto.builder().canReview(eligible).reason(eligible ? null : NOT_ELIGIBLE).build();
    }

    @Transactional
    public ReviewDto create(String username, Long productId, ReviewRequestDto request) {
        ProductEntity product = requireProduct(productId);
        if (!hasReceived(username, productId)) {
            throw new CustomException(NOT_ELIGIBLE, HttpStatus.FORBIDDEN);
        }
        if (reviewRepository.findByProductIdAndUsername(productId, username).isPresent()) {
            throw new CustomException(ALREADY_REVIEWED, HttpStatus.CONFLICT);
        }
        ProductReviewEntity review = ProductReviewEntity.builder()
                .product(product)
                .username(username)
                .status(ReviewStatus.PENDING)
                .build();
        applyContent(request, review);
        return toDto(reviewRepository.saveAndFlush(review), true);
    }

    @Transactional
    public ReviewDto updateMine(String username, Long productId, ReviewRequestDto request) {
        requireProduct(productId);
        ProductReviewEntity review = findMine(username, productId);
        applyContent(request, review);
        review.setStatus(ReviewStatus.PENDING);
        review.setModerationNote(null);
        review.setModeratedBy(null);
        return toDto(reviewRepository.saveAndFlush(review), true);
    }

    @Transactional
    public void deleteMine(String username, Long productId) {
        requireProduct(productId);
        reviewRepository.delete(findMine(username, productId));
    }

    @Transactional(readOnly = true)
    public Page<ReviewDto> forModeration(ReviewStatus status, Pageable pageable) {
        Page<ProductReviewEntity> page = status == null
                ? reviewRepository.findAllBy(pageable)
                : reviewRepository.findByStatus(status, pageable);
        return page.map(review -> toDto(review, true));
    }

    @Transactional
    public ReviewDto moderate(Long reviewId, ModerationRequestDto request, String moderator) {
        ProductReviewEntity review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomException("Review not found", HttpStatus.NOT_FOUND));
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (request.getDecision() == ModerationDecision.REJECTED && note == null) {
            throw new CustomException("A note is required when rejecting a review", HttpStatus.BAD_REQUEST);
        }
        review.setStatus(request.getDecision() == ModerationDecision.APPROVED
                ? ReviewStatus.APPROVED
                : ReviewStatus.REJECTED);
        review.setModerationNote(note);
        review.setModeratedBy(moderator);
        return toDto(reviewRepository.saveAndFlush(review), true);
    }

    private boolean hasReceived(String username, Long productId) {
        return orderRepository.existsOrderedProductWithStatus(username, productId, OrderStatus.DELIVERED);
    }

    private ProductReviewEntity findMine(String username, Long productId) {
        return reviewRepository.findByProductIdAndUsername(productId, username)
                .orElseThrow(() -> new CustomException("Review not found", HttpStatus.NOT_FOUND));
    }

    private ProductEntity requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
    }

    private static void applyContent(ReviewRequestDto request, ProductReviewEntity review) {
        review.setRating(request.getRating());
        review.setTitle(request.getTitle().trim());
        review.setBody(request.getBody().trim());
    }

    private static ReviewDto toDto(ProductReviewEntity review, boolean includeModeration) {
        return ReviewDto.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .username(review.getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .body(review.getBody())
                .status(review.getStatus())
                .moderationNote(includeModeration ? review.getModerationNote() : null)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
