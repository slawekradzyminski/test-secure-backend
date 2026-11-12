package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.StockStatus;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.review.ModerationDecision;
import com.awesome.testing.dto.review.ModerationRequestDto;
import com.awesome.testing.dto.review.MyReviewDto;
import com.awesome.testing.dto.review.ReviewDto;
import com.awesome.testing.dto.review.ReviewRequestDto;
import com.awesome.testing.dto.review.ReviewSummaryDto;
import com.awesome.testing.dto.wishlist.WishlistItemDto;
import com.awesome.testing.dto.wishlist.WishlistUpdateDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import com.awesome.testing.repository.EmailEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewAndWishlistFlowTest extends AbstractCommerceFlowTest {

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void onlyCustomersWithADeliveredOrderCanReview() {
        ProductEntity product = product("10.00", 10);
        OrderDto order = order(clientToken, product, 1);

        ResponseEntity<ErrorDto> beforeDelivery = postReview(clientToken, product, 5, ErrorDto.class);
        MyReviewDto eligibilityBefore = mine(clientToken, product);
        deliver(order.getId());
        MyReviewDto eligibilityAfter = mine(clientToken, product);
        ResponseEntity<ReviewDto> afterDelivery = postReview(clientToken, product, 5, ReviewDto.class);
        ResponseEntity<ErrorDto> second = postReview(clientToken, product, 4, ErrorDto.class);
        ResponseEntity<ErrorDto> stranger = postReview(otherClientToken, product, 1, ErrorDto.class);

        assertThat(beforeDelivery.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(eligibilityBefore.isCanReview()).isFalse();
        assertThat(eligibilityAfter.isCanReview()).isTrue();
        assertThat(afterDelivery.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(afterDelivery.getBody().getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void onlyApprovedReviewsArePublicAndCountTowardsTheSummary() {
        ProductEntity product = product("10.00", 10);
        String secondBuyer = otherClientToken;
        deliver(order(clientToken, product, 1).getId());
        deliver(order(secondBuyer, product, 1).getId());
        ReviewDto five = postReview(clientToken, product, 5, ReviewDto.class).getBody();
        ReviewDto two = postReview(secondBuyer, product, 2, ReviewDto.class).getBody();

        ReviewSummaryDto beforeModeration = summary(product);
        ResponseEntity<ErrorDto> rejectWithoutNote = moderate(two.getId(), ModerationDecision.REJECTED, null, ErrorDto.class);
        moderate(five.getId(), ModerationDecision.APPROVED, null, ReviewDto.class);
        moderate(two.getId(), ModerationDecision.REJECTED, "Off-topic", ReviewDto.class);
        ReviewSummaryDto afterModeration = summary(product);
        PageDto<ReviewDto> publicList = executeGet(reviews(product), getHeadersWith(otherClientToken),
                new ParameterizedTypeReference<PageDto<ReviewDto>>() {}).getBody();
        MyReviewDto rejectedAuthorView = mine(secondBuyer, product);

        assertThat(beforeModeration.getReviewCount()).isZero();
        assertThat(beforeModeration.getAverageRating()).isNull();
        assertThat(rejectWithoutNote.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(afterModeration.getReviewCount()).isEqualTo(1);
        assertThat(afterModeration.getAverageRating()).isEqualByComparingTo("5.0");
        assertThat(afterModeration.getDistribution()).containsEntry(5, 1L).containsEntry(2, 0L);
        assertThat(publicList.getContent()).extracting(ReviewDto::getId).containsExactly(five.getId());
        assertThat(publicList.getContent().getFirst().getModerationNote()).isNull();
        assertThat(rejectedAuthorView.getReview().getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(rejectedAuthorView.getReview().getModerationNote()).isEqualTo("Off-topic");
    }

    @Test
    void editingAnApprovedReviewSendsItBackToModeration() {
        ProductEntity product = product("10.00", 10);
        deliver(order(clientToken, product, 1).getId());
        ReviewDto review = postReview(clientToken, product, 4, ReviewDto.class).getBody();
        moderate(review.getId(), ModerationDecision.APPROVED, null, ReviewDto.class);

        ResponseEntity<ReviewDto> edited = executePut(reviews(product) + "/mine",
                new ReviewRequestDto(3, "Changed my mind", "Battery is weaker after a month of use."),
                getHeadersWith(clientToken), ReviewDto.class);

        assertThat(edited.getBody().getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(summary(product).getReviewCount()).isZero();
    }

    @Test
    void wishlistUpsertDistinguishesCreateFromUpdate() {
        ProductEntity product = product("10.00", 0);

        ResponseEntity<WishlistItemDto> created = executePut("/api/v1/wishlist/" + product.getId(),
                new WishlistUpdateDto(false), getHeadersWith(clientToken), WishlistItemDto.class);
        ResponseEntity<WishlistItemDto> updated = executePut("/api/v1/wishlist/" + product.getId(),
                new WishlistUpdateDto(true), getHeadersWith(clientToken), WishlistItemDto.class);
        List<WishlistItemDto> list = executeGet("/api/v1/wishlist", getHeadersWith(clientToken),
                new ParameterizedTypeReference<List<WishlistItemDto>>() {}).getBody();
        ResponseEntity<Void> removed = executeDelete("/api/v1/wishlist/" + product.getId(), getHeadersWith(clientToken), Void.class);
        ResponseEntity<ErrorDto> removedAgain = executeDelete("/api/v1/wishlist/" + product.getId(), getHeadersWith(clientToken), ErrorDto.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list).singleElement().satisfies(item -> {
            assertThat(item.getNotifyWhenAvailable()).isTrue();
            assertThat(item.getStockStatus()).isEqualTo(StockStatus.OUT_OF_STOCK);
        });
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(removedAgain.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void restockingASoldOutProductEmailsOnlyWatchersWhoAskedForIt() {
        ProductEntity product = product("10.00", 0);
        executePut("/api/v1/wishlist/" + product.getId(), new WishlistUpdateDto(true), getHeadersWith(clientToken), WishlistItemDto.class);
        executePut("/api/v1/wishlist/" + product.getId(), new WishlistUpdateDto(false), getHeadersWith(otherClientToken), WishlistItemDto.class);

        adjust(product, 3);
        adjust(product, 2);

        List<EmailEventEntity> backInStock = emailEventRepository.findAll().stream()
                .filter(event -> event.getType() == EmailTemplate.BACK_IN_STOCK)
                .toList();
        assertThat(backInStock).singleElement()
                .satisfies(event -> assertThat(event.getRecipientEmail()).isEqualTo(client.getEmail()));
        List<WishlistItemDto> list = executeGet("/api/v1/wishlist", getHeadersWith(clientToken),
                new ParameterizedTypeReference<List<WishlistItemDto>>() {}).getBody();
        assertThat(list.getFirst().getLastNotifiedAt()).isNotNull();
    }

    @Test
    void blankReviewFieldsAlwaysGetTheSameValidationMessages() {
        // given: "" used to fail both @NotBlank and @Size(min), and the reported message varied per request
        ProductEntity product = product("10.00", 10);

        // when
        List<ResponseEntity<Map>> responses = IntStream.range(0, 10)
                .mapToObj(attempt -> executePost(reviews(product), new ReviewRequestDto(5, "", ""),
                        getHeadersWith(clientToken), Map.class))
                .toList();

        // then
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isEqualTo(Map.of(
                    "title", "Title is required",
                    "body", "Review text is required"));
        });
    }

    private void adjust(ProductEntity product, int delta) {
        ResponseEntity<String> response = executePost("/api/v1/admin/inventory/" + product.getId() + "/adjustments",
                InventoryAdjustmentDto.builder().delta(delta).reason("restock").requestId(UUID.randomUUID()).build(),
                getHeadersWith(adminToken), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String reviews(ProductEntity product) {
        return PRODUCTS_ENDPOINT + "/" + product.getId() + "/reviews";
    }

    private <T> ResponseEntity<T> postReview(String token, ProductEntity product, int rating, Class<T> type) {
        return executePost(reviews(product),
                new ReviewRequestDto(rating, "Honest opinion", "I have used this product for a week now."),
                getHeadersWith(token), type);
    }

    private MyReviewDto mine(String token, ProductEntity product) {
        return executeGet(reviews(product) + "/mine", getHeadersWith(token), MyReviewDto.class).getBody();
    }

    private ReviewSummaryDto summary(ProductEntity product) {
        return executeGet(reviews(product) + "/summary", getHeadersWith(clientToken), ReviewSummaryDto.class).getBody();
    }

    private <T> ResponseEntity<T> moderate(Long reviewId, ModerationDecision decision, String note, Class<T> type) {
        return executePut("/api/v1/admin/reviews/" + reviewId + "/moderation",
                new ModerationRequestDto(decision, note), getHeadersWith(adminToken), type);
    }
}
