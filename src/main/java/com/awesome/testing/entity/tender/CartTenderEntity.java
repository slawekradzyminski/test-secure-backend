package com.awesome.testing.entity.tender;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** The gift card and loyalty points a shopper applied to the cart; checkout uses and clears them. */
@Entity
@Table(name = "cart_tenders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartTenderEntity {

    @Id
    @Column(nullable = false)
    private String username;

    @Column(name = "gift_card_code", length = 19)
    private String giftCardCode;

    @Column
    private Long points;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEmpty() {
        return giftCardCode == null && points == null;
    }
}
