package com.awesome.testing.dto.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wishlist entry settings")
public class WishlistUpdateDto {
    @Schema(types = {"boolean", "null"}, description = "Email when back in stock; defaults to false", example = "true")
    private Boolean notifyWhenAvailable;
}
