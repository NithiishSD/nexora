package com.nexora.orders.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderItem;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID>,
        JpaSpecificationExecutor<CustomerOrder> {

    // SELECT ... FOR UPDATE on the order row: status changes of ONE order (deliver, cancel, edit ...) run one after
    // the other, so a check and the change it guards cannot be raced. Plain query (no join fetch, see
    // 03-ARCHITECTURE 14); the items are loaded afterwards in the same transaction.
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);

    // The customer of an order without loading (or locking) the order: its party row is locked first (ADR-028).
    @Query("select o.customerId from CustomerOrder o where o.id = :id")
    Optional<UUID> findCustomerId(@Param("id") UUID id);

    // Items with their order for other modules' references (one query, no N+1).
    @Query("select i from OrderItem i join fetch i.order where i.id in :ids")
    List<OrderItem> findItemsWithOrder(@Param("ids") Collection<UUID> ids);

    // The orders of some items, locked in ascending id as the DATABASE orders UUIDs (the same order as the payments'
    // obligation locks; Java's UUID.compareTo sorts differently). Nothing is loaded before the lock.
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id in (select i.order.id from OrderItem i where i.id in :ids)"
            + " order by o.id")
    List<CustomerOrder> findOrdersOfItemsForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("select i.id from OrderItem i where i.order.id = :orderId")
    List<UUID> findItemIds(@Param("orderId") UUID orderId);
}
