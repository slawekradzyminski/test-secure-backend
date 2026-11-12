package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.product.search.CategoryFacetDto;
import com.awesome.testing.dto.product.search.ProductSearchItemDto;
import com.awesome.testing.dto.product.search.ProductSearchResultDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.review.ReviewStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchControllerTest extends AbstractProductSearchTest {

    @Test
    void shouldReturnEveryProductNewestFirstWithDefaultPagingAndFacets() {
        // given
        product("Alpha", "First", "Books", "10.00", 0);
        product("Beta", "Second", "Books", "30.00", 5);
        product("Gamma", "Third", "Garden", "20.00", 2);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductSearchResultDto body = response.getBody();
        assertThat(names(response)).containsExactly("Gamma", "Beta", "Alpha");
        assertThat(body.pageNumber()).isZero();
        assertThat(body.pageSize()).isEqualTo(20);
        assertThat(body.totalElements()).isEqualTo(3);
        assertThat(body.totalPages()).isEqualTo(1);
        assertThat(body.facets().categories())
                .containsExactly(new CategoryFacetDto("Books", 2), new CategoryFacetDto("Garden", 1));
        assertThat(body.facets().priceRange().min()).isEqualByComparingTo("10.00");
        assertThat(body.facets().priceRange().max()).isEqualByComparingTo("30.00");
        assertThat(body.facets().inStockCount()).isEqualTo(2);
    }

    @Test
    void shouldReturnProductFieldsWithRatingOfApprovedReviewsOnly() {
        // given
        ProductEntity rated = product("Rated lamp", "Desk lamp", "Home", "15.50", 3);
        review(rated, 5, ReviewStatus.APPROVED);
        review(rated, 4, ReviewStatus.APPROVED);
        review(rated, 4, ReviewStatus.APPROVED);
        review(rated, 1, ReviewStatus.PENDING);
        review(rated, 1, ReviewStatus.REJECTED);
        product("Unrated lamp", "Floor lamp", "Home", "25.00", 3);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?sort=name_asc");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProductSearchItemDto first = response.getBody().content().getFirst();
        assertThat(first.getId()).isEqualTo(rated.getId());
        assertThat(first.getDescription()).isEqualTo("Desk lamp");
        assertThat(first.getCategory()).isEqualTo("Home");
        assertThat(first.getPrice()).isEqualByComparingTo("15.50");
        assertThat(first.getStockQuantity()).isEqualTo(3);
        assertThat(first.getCreatedAt()).isNotNull();
        assertThat(first.getUpdatedAt()).isNotNull();
        assertThat(first.getAverageRating()).isEqualByComparingTo("4.3");
        assertThat(first.getReviewCount()).isEqualTo(3);
        ProductSearchItemDto second = response.getBody().content().get(1);
        assertThat(second.getAverageRating()).isNull();
        assertThat(second.getReviewCount()).isZero();
    }

    @Test
    void shouldMatchTrimmedQueryCaseInsensitivelyInNameCategoryAndDescription() {
        // given
        product("Wireless MOUSE", "Ergonomic", "Accessories", "20.00", 1);
        product("Pad", "Large pad for a mouse", "Accessories", "5.00", 1);
        product("Trap", "Humane", "Mousetraps", "8.00", 1);
        product("Keyboard", "Mechanical", "Accessories", "60.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?q=  mOuSe ");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response)).containsExactly("Wireless MOUSE", "Trap", "Pad");
        assertThat(response.getBody().totalElements()).isEqualTo(3);
    }

    @Test
    void shouldRankNameMatchesAboveCategoryAboveDescriptionAndAddScores() {
        // given
        product("Desc only", "a chair for you", "Misc", "1.00", 1);
        product("Category only", "Plain", "Chairs", "1.00", 1);
        product("Chair", "Plain", "Misc", "1.00", 1);
        product("Armchair", "Comfortable chair", "Chairs", "1.00", 1);
        product("Name and description chair", "chair", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?q=chair");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response)).containsExactly(
                "Armchair", "Name and description chair", "Chair", "Category only", "Desc only");
    }

    @Test
    void shouldTreatPercentAndUnderscoreAsLiteralCharacters() {
        // given
        product("50% off mug", "Sale", "Kitchen", "3.00", 1);
        product("500 mug", "Regular", "Kitchen", "3.00", 1);
        product("snake_case cup", "Code", "Kitchen", "3.00", 1);
        product("snakeXcase cup", "Code", "Kitchen", "3.00", 1);
        product("back\\slash plate", "Code", "Kitchen", "3.00", 1);
        product("backslash plate", "Code", "Kitchen", "3.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> percent = search("?q=50%");
        ResponseEntity<ProductSearchResultDto> underscore = search("?q=e_c");
        ResponseEntity<ProductSearchResultDto> backslash = search("?q=k\\s");

        // then
        assertThat(names(percent)).containsExactly("50% off mug");
        assertThat(names(underscore)).containsExactly("snake_case cup");
        assertThat(names(backslash)).containsExactly("back\\slash plate");
    }

    @Test
    void shouldFilterByRepeatedAndCommaSeparatedCategoriesWhileFacetsIgnoreTheCategoryFilter() {
        // given
        product("Novel", "Story", "Books", "12.00", 1);
        product("Hose", "Water", "Garden", "25.00", 0);
        product("Drill", "Power", "Tools", "80.00", 4);
        product("Saw", "Hand", "Tools", "40.00", 0);

        // when
        ResponseEntity<ProductSearchResultDto> repeated = search("?category=books&category=GARDEN&sort=name_asc");
        ResponseEntity<ProductSearchResultDto> commaList = search("?category=books, garden&sort=name_asc");

        // then
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(repeated)).containsExactly("Hose", "Novel");
        assertThat(names(commaList)).containsExactly("Hose", "Novel");
        assertThat(repeated.getBody().totalElements()).isEqualTo(2);
        assertThat(repeated.getBody().facets().categories()).containsExactly(
                new CategoryFacetDto("Tools", 2), new CategoryFacetDto("Books", 1), new CategoryFacetDto("Garden", 1));
        assertThat(repeated.getBody().facets().priceRange().min()).isEqualByComparingTo("12.00");
        assertThat(repeated.getBody().facets().priceRange().max()).isEqualByComparingTo("80.00");
        assertThat(repeated.getBody().facets().inStockCount()).isEqualTo(2);
    }

    @Test
    void shouldComputeFacetsOverTheOtherFilters() {
        // given
        product("Cheap book", "Story", "Books", "5.00", 1);
        product("Mid book", "Story", "Books", "15.00", 0);
        product("Mid tool", "Story", "Tools", "18.00", 3);
        product("Pricey tool", "Story", "Tools", "90.00", 3);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?minPrice=10&maxPrice=50&category=tools");

        // then
        assertThat(names(response)).containsExactly("Mid tool");
        assertThat(response.getBody().facets().categories())
                .containsExactly(new CategoryFacetDto("Books", 1), new CategoryFacetDto("Tools", 1));
        assertThat(response.getBody().facets().priceRange().min()).isEqualByComparingTo("15.00");
        assertThat(response.getBody().facets().priceRange().max()).isEqualByComparingTo("18.00");
        assertThat(response.getBody().facets().inStockCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyPageAndNullPriceRangeWhenNothingMatches() {
        // given
        product("Novel", "Story", "Books", "12.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?q=nothing-like-this");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).isEmpty();
        assertThat(response.getBody().totalElements()).isZero();
        assertThat(response.getBody().totalPages()).isZero();
        assertThat(response.getBody().facets().categories()).isEmpty();
        assertThat(response.getBody().facets().priceRange().min()).isNull();
        assertThat(response.getBody().facets().priceRange().max()).isNull();
        assertThat(response.getBody().facets().inStockCount()).isZero();
    }

    @Test
    void shouldFilterByInclusivePriceBounds() {
        // given
        product("Below", "x", "Misc", "9.99", 1);
        product("Lower edge", "x", "Misc", "10.00", 1);
        product("Upper edge", "x", "Misc", "20.00", 1);
        product("Above", "x", "Misc", "20.01", 1);

        // when
        ResponseEntity<ProductSearchResultDto> both = search("?minPrice=10&maxPrice=20.00&sort=price_asc");
        ResponseEntity<ProductSearchResultDto> minOnly = search("?minPrice=20&sort=price_asc");
        ResponseEntity<ProductSearchResultDto> maxOnly = search("?maxPrice=10&sort=price_asc");
        ResponseEntity<ProductSearchResultDto> equal = search("?minPrice=10&maxPrice=10");

        // then
        assertThat(names(both)).containsExactly("Lower edge", "Upper edge");
        assertThat(names(minOnly)).containsExactly("Upper edge", "Above");
        assertThat(names(maxOnly)).containsExactly("Below", "Lower edge");
        assertThat(names(equal)).containsExactly("Lower edge");
    }

    @Test
    void shouldFilterByInStockOnlyWhenTrue() {
        // given
        product("Sold out", "x", "Misc", "1.00", 0);
        product("Available", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> inStock = search("?inStock=true");
        ResponseEntity<ProductSearchResultDto> notFiltered = search("?inStock=false&sort=name_asc");

        // then
        assertThat(names(inStock)).containsExactly("Available");
        assertThat(names(notFiltered)).containsExactly("Available", "Sold out");
    }

    @Test
    void shouldFilterByMinimumAverageOfApprovedReviews() {
        // given
        ProductEntity excellent = product("Excellent", "x", "Misc", "1.00", 1);
        review(excellent, 5, ReviewStatus.APPROVED);
        ProductEntity exactlyFour = product("Exactly four", "x", "Misc", "1.00", 1);
        review(exactlyFour, 5, ReviewStatus.APPROVED);
        review(exactlyFour, 3, ReviewStatus.APPROVED);
        ProductEntity pendingPraise = product("Pending praise", "x", "Misc", "1.00", 1);
        review(pendingPraise, 3, ReviewStatus.APPROVED);
        review(pendingPraise, 5, ReviewStatus.PENDING);
        product("Unreviewed", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?minRating=4&sort=name_asc");
        ResponseEntity<ProductSearchResultDto> lowest = search("?minRating=1&sort=name_asc");

        // then
        assertThat(names(response)).containsExactly("Exactly four", "Excellent");
        assertThat(names(lowest)).containsExactly("Exactly four", "Excellent", "Pending praise");
    }

    @Test
    void shouldSortByPriceAndNameWithIdAsTieBreaker() {
        // given
        ProductEntity first = product("banana", "x", "Misc", "2.00", 1);
        product("Apple", "x", "Misc", "3.00", 1);
        ProductEntity sameName = product("Banana", "x", "Misc", "2.00", 1);
        product("cherry", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> priceAsc = search("?sort=price_asc");
        ResponseEntity<ProductSearchResultDto> priceDesc = search("?sort=PRICE_DESC");
        ResponseEntity<ProductSearchResultDto> nameAsc = search("?sort=name_asc");

        // then
        assertThat(priceAsc.getBody().content()).extracting(ProductSearchItemDto::getId)
                .containsSubsequence(first.getId(), sameName.getId());
        assertThat(names(priceAsc)).containsExactly("cherry", "banana", "Banana", "Apple");
        assertThat(names(priceDesc)).containsExactly("Apple", "banana", "Banana", "cherry");
        assertThat(names(nameAsc)).containsExactly("Apple", "banana", "Banana", "cherry");
    }

    @Test
    void shouldSortByRatingThenReviewCountWithUnratedProductsLast() {
        // given
        ProductEntity oneFive = product("One five", "x", "Misc", "1.00", 1);
        review(oneFive, 5, ReviewStatus.APPROVED);
        ProductEntity twoFives = product("Two fives", "x", "Misc", "1.00", 1);
        review(twoFives, 5, ReviewStatus.APPROVED);
        review(twoFives, 5, ReviewStatus.APPROVED);
        ProductEntity average = product("Average", "x", "Misc", "1.00", 1);
        review(average, 3, ReviewStatus.APPROVED);
        review(average, 5, ReviewStatus.PENDING);
        product("A unrated", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> response = search("?sort=rating_desc");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response)).containsExactly("Two fives", "One five", "Average", "A unrated");
    }

    @Test
    void shouldUseNewestForRelevanceWithoutQueryAndAllowExplicitSortWithQuery() {
        // given
        product("Lamp one", "x", "Misc", "9.00", 1);
        product("Lamp two", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> relevanceWithoutQuery = search("?sort=relevance");
        ResponseEntity<ProductSearchResultDto> priceWithQuery = search("?q=lamp&sort=price_asc");
        ResponseEntity<ProductSearchResultDto> blankQuery = search("?q=  ");

        // then
        assertThat(names(relevanceWithoutQuery)).containsExactly("Lamp two", "Lamp one");
        assertThat(names(priceWithQuery)).containsExactly("Lamp two", "Lamp one");
        assertThat(names(blankQuery)).containsExactly("Lamp two", "Lamp one");
    }

    @Test
    void shouldPageThroughResultsAndReportTotals() {
        // given
        for (int index = 1; index <= 5; index++) {
            product("Item " + index, "x", "Misc", index + ".00", 1);
        }

        // when
        ResponseEntity<ProductSearchResultDto> secondPage = search("?sort=price_asc&page=1&size=2");
        ResponseEntity<ProductSearchResultDto> lastPage = search("?sort=price_asc&page=2&size=2");
        ResponseEntity<ProductSearchResultDto> beyond = search("?sort=price_asc&page=9&size=2");
        ResponseEntity<ProductSearchResultDto> largest = search("?size=100");

        // then
        assertThat(names(secondPage)).containsExactly("Item 3", "Item 4");
        assertThat(secondPage.getBody().pageNumber()).isEqualTo(1);
        assertThat(secondPage.getBody().pageSize()).isEqualTo(2);
        assertThat(secondPage.getBody().totalElements()).isEqualTo(5);
        assertThat(secondPage.getBody().totalPages()).isEqualTo(3);
        assertThat(names(lastPage)).containsExactly("Item 5");
        assertThat(beyond.getBody().content()).isEmpty();
        assertThat(beyond.getBody().totalElements()).isEqualTo(5);
        assertThat(beyond.getBody().facets().categories()).containsExactly(new CategoryFacetDto("Misc", 5));
        assertThat(largest.getBody().content()).hasSize(5);
        assertThat(largest.getBody().pageSize()).isEqualTo(100);
    }

    @Test
    void shouldCombineEveryFilter() {
        // given
        ProductEntity match = product("Garden chair", "Teak", "Garden", "120.00", 2);
        review(match, 5, ReviewStatus.APPROVED);
        ProductEntity outOfStock = product("Garden chair deluxe", "Teak", "Garden", "150.00", 0);
        review(outOfStock, 5, ReviewStatus.APPROVED);
        ProductEntity tooCheap = product("Garden chair mini", "Teak", "Garden", "20.00", 2);
        review(tooCheap, 5, ReviewStatus.APPROVED);
        product("Office chair", "Mesh", "Office", "130.00", 2);

        // when
        ResponseEntity<ProductSearchResultDto> response = search(
                "?q=chair&category=garden&minPrice=100&maxPrice=200&minRating=4.5&inStock=true");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response)).containsExactly("Garden chair");
        assertThat(response.getBody().content().getFirst().getAverageRating()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(response.getBody().facets().categories()).containsExactly(new CategoryFacetDto("Garden", 1));
    }

    @Test
    void shouldIgnoreDiacriticsInTheQueryAndTheCatalog() {
        // given
        product("Zażółć Gęślą", "Polish pangram", "Books", "10.00", 1);
        product("Crème brûlée torch", "Kitchen", "Łódź Kitchen", "20.00", 1);
        product("Plain", "Nothing special", "Misc", "30.00", 1);

        // when
        ResponseEntity<ProductSearchResultDto> plainQuery = search("?q=zazolc");
        ResponseEntity<ProductSearchResultDto> accentedQuery = search("?q=ZAŻÓŁĆ");
        ResponseEntity<ProductSearchResultDto> mixedQuery = search("?q=gesla");
        ResponseEntity<ProductSearchResultDto> category = search("?q=lodz");
        ResponseEntity<ProductSearchResultDto> french = search("?q=creme bru");

        // then
        assertThat(names(plainQuery)).containsExactly("Zażółć Gęślą");
        assertThat(names(accentedQuery)).containsExactly("Zażółć Gęślą");
        assertThat(names(mixedQuery)).containsExactly("Zażółć Gęślą");
        assertThat(names(category)).containsExactly("Crème brûlée torch");
        assertThat(names(french)).containsExactly("Crème brûlée torch");
    }
}
