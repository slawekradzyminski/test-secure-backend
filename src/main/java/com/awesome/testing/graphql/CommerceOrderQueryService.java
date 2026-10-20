package com.awesome.testing.graphql;

import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.graphql.CommerceViews.Order;
import com.awesome.testing.graphql.CommerceViews.ResultPage;
import com.awesome.testing.repository.OrderRepository;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("graphql")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Transactional(readOnly = true, timeout = 5)
public class CommerceOrderQueryService {
    private final CommerceAccess access;
    private final OrderRepository orderRepository;

    public ResultPage<Order> orders(String username, OrderStatus status, int page, int size, PageSelection selection) {
        PageRequest pagination = PageRequest.of(page, size, Sort.by("id").ascending());
        String owner = access.isAdmin() && username == null ? null : access.owner(username);
        Specification<OrderEntity> filter = filter(owner, status);
        if (!selection.items()) {
            return new ResultPage<>(
                    List.of(), selection.total() ? Math.toIntExact(orderRepository.count(filter)) : 0, page, size);
        }
        Slice<OrderEntity> rows =
                selection.total()
                        ? orderRepository.findAll(filter, pagination)
                        : orderRepository.findBy(filter, query -> query.slice(pagination));
        List<Order> content = selectedOrders(rows, selection.orderItems());
        int total = rows instanceof Page<?> result ? Math.toIntExact(result.getTotalElements()) : 0;
        return new ResultPage<>(content, total, page, size);
    }

    private static Specification<OrderEntity> filter(String owner, OrderStatus status) {
        return (root, query, cb) -> {
            Predicate ownership = owner == null ? cb.conjunction() : cb.equal(root.get("username"), owner);
            return status == null ? ownership : cb.and(ownership, cb.equal(root.get("status"), status));
        };
    }

    private List<Order> selectedOrders(Slice<OrderEntity> rows, boolean includeItems) {
        if (!includeItems || !rows.hasContent()) {
            return rows.getContent().stream().map(Order::summary).toList();
        }
        List<Long> ids = rows.getContent().stream().map(OrderEntity::getId).toList();
        Map<Long, OrderEntity> loaded =
                orderRepository.findOrdersWithItemsByIds(ids).stream()
                        .collect(Collectors.toMap(OrderEntity::getId, Function.identity()));
        return ids.stream().map(loaded::get).map(OrderDto::from).map(Order::from).toList();
    }
}
