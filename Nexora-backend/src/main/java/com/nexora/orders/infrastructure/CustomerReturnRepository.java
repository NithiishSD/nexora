package com.nexora.orders.infrastructure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexora.orders.domain.CustomerReturn;

public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, UUID> {

    List<CustomerReturn> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<CustomerReturn> findByOrderItemId(UUID orderItemId);

    Optional<CustomerReturn> findByReturnNumber(String returnNumber);

    @Query("select coalesce(sum(r.returnedWeightKg), 0) from CustomerReturn r where r.orderItem.id = :itemId")
    BigDecimal sumReturnedWeightByItemId(@Param("itemId") UUID itemId);

    @Query("select r.orderItem.id, coalesce(sum(r.returnedWeightKg), 0) from CustomerReturn r"
            + " where r.orderItem.id in :itemIds group by r.orderItem.id")
    List<Object[]> sumReturnedWeightByItemIds(@Param("itemIds") Collection<UUID> itemIds);
}
