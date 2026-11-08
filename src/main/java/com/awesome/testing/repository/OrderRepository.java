package com.awesome.testing.repository;

import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(@Param("id") Long id);

    void deleteByUsername(String username);

    @Query("select count(i) > 0 from OrderItemEntity i "
            + "where i.order.username = :username and i.product.id = :productId and i.order.status = :status")
    boolean existsOrderedProductWithStatus(
            @Param("username") String username, @Param("productId") Long productId, @Param("status") OrderStatus status);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status "
            + "AND NOT EXISTS (SELECT s.id FROM ShipmentEntity s WHERE s.order = o) ORDER BY o.createdAt ASC, o.id ASC")
    Page<Long> findOrderIdsAwaitingShipment(@Param("status") OrderStatus status, Pageable pageable);

    default Page<OrderEntity> findOrdersAwaitingShipment(Pageable pageable) {
        return loadPageWithItems(findOrderIdsAwaitingShipment(OrderStatus.PAID, pageable), pageable);
    }

    long countByUsername(String username);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.username = :username")
    Page<Long> findOrderIdsByUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.username = :username AND o.status = :status")
    Page<Long> findOrderIdsByUsernameAndStatus(
            @Param("username") String username, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o")
    Page<Long> findAllOrderIds(Pageable pageable);

    @Query("SELECT o.id FROM OrderEntity o WHERE o.status = :status")
    Page<Long> findAllOrderIdsByStatus(@Param("status") OrderStatus status, Pageable pageable);

    @Query(
            "SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id IN :ids")
    List<OrderEntity> findOrdersWithItemsByIds(@Param("ids") List<Long> ids);

    @Query(
            "SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product WHERE o.id = :id AND o.username = :username")
    Optional<OrderEntity> findByIdAndUsername(@Param("id") Long id, @Param("username") String username);

    default Page<OrderEntity> findByUsername(@Param("username") String username, Pageable pageable) {
        Page<Long> idPage = findOrderIdsByUsername(username, pageable);
        return loadPageWithItems(idPage, pageable);
    }

    default Page<OrderEntity> findByUsernameAndStatus(
            @Param("username") String username, @Param("status") OrderStatus status, Pageable pageable) {
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
        Map<Long, OrderEntity> ordersById =
                findOrdersWithItemsByIds(ids).stream()
                        .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));

        List<OrderEntity> ordered = ids.stream().map(ordersById::get).filter(order -> order != null).toList();

        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
    }
}
