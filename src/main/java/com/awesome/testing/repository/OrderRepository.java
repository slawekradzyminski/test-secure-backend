package com.awesome.testing.repository;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.dto.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    @Query("SELECT o.id FROM OrderEntity o WHERE o.username = :username")
    Page<Long> findOrderIdsByUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.username = :username AND o.status = :status")
    Page<Long> findOrderIdsByUsernameAndStatus(@Param("username") String username,
                                               @Param("status") OrderStatus status,
                                               Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o")
    Page<Long> findAllOrderIds(Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status")
    Page<Long> findAllOrderIdsByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id IN :ids")
    List<OrderEntity> findOrdersWithItemsByIds(@Param("ids") List<Long> ids);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.username = :username")
    Optional<OrderEntity> findByIdAndUsername(@Param("id") Long id, @Param("username") String username);


    default Page<OrderEntity> findByUsername(@Param("username") String username, Pageable pageable) {
        Page<Long> idPage = findOrderIdsByUsername(username, pageable);
        return loadPageWithItems(idPage, pageable);
    }

    default Page<OrderEntity> findByUsernameAndStatus(@Param("username") String username,
                                                      @Param("status") OrderStatus status,
                                                      Pageable pageable) {
        Page<Long> idPage = findOrderIdsByUsernameAndStatus(username, status, pageable);
        return loadPageWithItems(idPage, pageable);
    }

    default Page<OrderEntity> findAllOrdersWithItems(Pageable pageable) {
        Page<Long> idPage = findAllOrderIds(pageable);
        return loadPageWithItems(idPage, pageable);
    }

    default Page<OrderEntity> findAllOrdersByStatus(@Param("status") OrderStatus status, Pageable pageable) {
        Page<Long> idPage = findAllOrderIdsByStatus(status, pageable);
        return loadPageWithItems(idPage, pageable);
    }

    private Page<OrderEntity> loadPageWithItems(Page<Long> idPage, Pageable pageable) {
        if (idPage.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        List<Long> ids = idPage.getContent();
        Map<Long, OrderEntity> ordersById = findOrdersWithItemsByIds(ids).stream()
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));

        List<OrderEntity> ordered = ids.stream()
                .map(ordersById::get)
                .filter(order -> order != null)
                .toList();

        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }
}
