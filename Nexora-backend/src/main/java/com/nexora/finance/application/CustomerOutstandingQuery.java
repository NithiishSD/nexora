package com.nexora.finance.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.finance.api.dto.OutstandingResponse;
import com.nexora.finance.api.dto.OutstandingResponse.Ageing;
import com.nexora.finance.api.dto.OutstandingResponse.OpeningLine;
import com.nexora.finance.api.dto.OutstandingResponse.OrderLine;
import com.nexora.finance.infrastructure.ObligationQueryRepository;
import com.nexora.finance.infrastructure.ObligationQueryRepository.Advance;
import com.nexora.parties.application.PartyRef;
import com.nexora.parties.application.PartyService;
import com.nexora.parties.domain.PartyRole;
import com.nexora.shared.domain.Money;

// What a customer still owes (FR-PAY-17/18), computed on read by SQL aggregation; never stored. The first release
// shows the age of each amount only: no customer credit period and no due-date alerts (FR-PARTY-17 is post-MVP).
@Service
public class CustomerOutstandingQuery {

    private final ObligationQueryRepository obligations;
    private final PartyService parties;
    private final Clock clock;

    public CustomerOutstandingQuery(ObligationQueryRepository obligations, PartyService parties, Clock clock) {
        this.obligations = obligations;
        this.parties = parties;
        this.clock = clock;
    }

    // 404 unknown, 422 not a customer; an inactive customer's balance is still shown.
    @Transactional(readOnly = true)
    public OutstandingResponse forCustomer(UUID customerId) {
        PartyRef customer = parties.requireRole(customerId, PartyRole.CUSTOMER);
        var party = parties.get(customerId);
        Integer creditDays = party.customerCreditDays();
        LocalDate today = LocalDate.now(clock);
        Buckets buckets = new Buckets();
        List<OrderLine> orders = obligations.customerOrderBalances(customerId).stream()
                .map(o -> {
                    int age = (int) ChronoUnit.DAYS.between(o.orderDate(), today);
                    BigDecimal outstanding = o.amount().subtract(o.allocated());
                    buckets.add(outstanding, age);
                    LocalDate dueDate = creditDays != null ? o.orderDate().plusDays(creditDays) : null;
                    Boolean overdue = dueDate != null ? today.isAfter(dueDate) : null;
                    return new OrderLine(o.orderId(), o.orderNumber(), o.orderDate(), o.status(), o.amount(),
                            o.allocated(), outstanding, age, dueDate, overdue);
                })
                .toList();
        List<OpeningLine> openings = obligations.openingBalances(customerId, "CUSTOMER").stream()
                .map(o -> {
                    int age = (int) ChronoUnit.DAYS.between(o.obligationDate(), today);
                    BigDecimal outstanding = o.originalAmount().subtract(o.allocated());
                    buckets.add(outstanding, age);
                    return new OpeningLine(o.id(), o.obligationDate(), o.referenceNote(), o.originalAmount(),
                            o.allocated(), outstanding, age);
                })
                .toList();
        BigDecimal advance = obligations.customerAdvances(customerId).stream().map(Advance::unallocated)
                .reduce(Money.zero(), BigDecimal::add);
        return new OutstandingResponse(customer, creditDays, buckets.total(), advance, today, buckets.ageing(), orders, openings);
    }

    private static final class Buckets {
        private BigDecimal d30 = Money.zero();
        private BigDecimal d60 = Money.zero();
        private BigDecimal d90 = Money.zero();
        private BigDecimal over = Money.zero();

        void add(BigDecimal amount, int ageDays) {
            if (ageDays <= 30) {
                d30 = d30.add(amount);
            } else if (ageDays <= 60) {
                d60 = d60.add(amount);
            } else if (ageDays <= 90) {
                d90 = d90.add(amount);
            } else {
                over = over.add(amount);
            }
        }

        BigDecimal total() {
            return d30.add(d60).add(d90).add(over);
        }

        Ageing ageing() {
            return new Ageing(d30, d60, d90, over);
        }
    }
}
