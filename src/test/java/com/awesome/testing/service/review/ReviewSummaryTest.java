package com.awesome.testing.service.review;

import com.awesome.testing.dto.review.ReviewSummaryDto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSummaryTest {

    @Test
    void averageIsWeightedByCountAndRoundedHalfUpToOneDecimal() {
        ReviewSummaryDto summary = ReviewService.summarize(1L, distribution(0, 0, 1, 2, 1));

        assertThat(summary.getReviewCount()).isEqualTo(4);
        assertThat(summary.getAverageRating()).isEqualByComparingTo("4.0");
        assertThat(ReviewService.summarize(1L, distribution(0, 0, 0, 1, 1)).getAverageRating()).isEqualByComparingTo("4.5");
        assertThat(ReviewService.summarize(1L, distribution(1, 0, 0, 0, 2)).getAverageRating()).isEqualByComparingTo("3.7");
    }

    @Test
    void averageIsNullWithoutReviews() {
        assertThat(ReviewService.summarize(1L, distribution(0, 0, 0, 0, 0)).getAverageRating()).isNull();
    }

    private static Map<Integer, Long> distribution(long... counts) {
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < counts.length; i++) {
            map.put(i + 1, counts[i]);
        }
        return map;
    }
}
