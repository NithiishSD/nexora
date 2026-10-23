package com.nexora.parties.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.FieldValidationException;
import com.nexora.shared.error.InvalidStateTransitionException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

// Any external person or business: customer, supplier, manufacturer, worker, lender (DOMAIN_RULES 4, Parties).
// Rich entity: every change goes through a method that checks the rules; there are no setters.
@Entity
@Table(name = "parties")
public class Party extends AuditableEntity {

    @Column(name = "party_code", nullable = false, length = 30, unique = true, updatable = false)
    private String partyCode;                      // PTY-2026-0001, never changes

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 155)
    private String area;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String pincode;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartyStatus status;

    @Column(name = "default_credit_days")
    private Integer defaultCreditDays;

    @Column(name = "customer_credit_days")
    private Integer customerCreditDays;

    @Column(name = "default_rolling_rate_per_hour", precision = 14, scale = 2)
    private BigDecimal defaultRollingRatePerHour;

    @Column(name = "default_warping_rate_per_kg", precision = 14, scale = 2)
    private BigDecimal defaultWarpingRatePerKg;

    // party_roles(party_id, role). A set: a role can be held once; adding inserts just the new row.
    // @BatchSize: when a page of parties touches their roles, Hibernate loads the roles of up to 100 parties
    // in ONE query instead of one query per party (the N+1 problem).
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "party_roles", joinColumns = @JoinColumn(name = "party_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<PartyRole> roles = EnumSet.noneOf(PartyRole.class);

    // party_phone_numbers. Replacing the list makes Hibernate delete the old rows BEFORE inserting the new
    // ones, so the "one primary per party" unique index never sees two primaries at once.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "party_phone_numbers", joinColumns = @JoinColumn(name = "party_id"))
    @OrderBy("primary desc, phoneNumber asc")
    private List<PartyPhoneNumber> phones = new ArrayList<>();

    protected Party() {
        // for JPA only
    }

    // Factory. The code comes from BusinessNumberGenerator (PTY prefix); a new party is always ACTIVE.
    public static Party create(String partyCode, PartyDetails details, Collection<PartyRole> roles,
                               List<PartyPhoneNumber> phones) {
        if (roles == null || roles.isEmpty()) {
            throw new FieldValidationException("roles", "A party needs at least one role.");
        }
        Party party = new Party();
        party.partyCode = partyCode;
        party.status = PartyStatus.ACTIVE;
        party.roles.addAll(roles);                  // duplicates collapse in the set
        party.update(details);
        party.replacePhones(phones);
        return party;
    }

    // Changes name, address, note and the role-specific defaults. Code, status and roles are not touched.
    public void update(PartyDetails details) {
        // defaults only make sense for the role that uses them (FR-PARTY-13/14)
        if (details.defaultCreditDays() != null && !roles.contains(PartyRole.SUPPLIER)) {
            throw new FieldValidationException("defaultCreditDays", "Default credit days are allowed only for a supplier.");
        }
        if (details.customerCreditDays() != null && !roles.contains(PartyRole.CUSTOMER)) {
            throw new FieldValidationException("customerCreditDays", "Customer credit days are allowed only for a customer.");
        }
        if (details.defaultRollingRatePerHour() != null && !roles.contains(PartyRole.WORKER)) {
            throw new FieldValidationException("defaultRollingRatePerHour", "Worker rates are allowed only for a worker.");
        }
        if (details.defaultWarpingRatePerKg() != null && !roles.contains(PartyRole.WORKER)) {
            throw new FieldValidationException("defaultWarpingRatePerKg", "Worker rates are allowed only for a worker.");
        }
        this.name = details.name().trim();
        this.addressLine = details.addressLine();
        this.area = details.area();
        this.city = details.city();
        this.district = details.district();
        this.state = details.state();
        this.pincode = details.pincode();
        this.note = details.note();
        this.defaultCreditDays = details.defaultCreditDays();
        this.customerCreditDays = details.customerCreditDays();
        // money is stored with exactly 2 decimals (HALF_UP); validation already limits input to 2 decimals
        this.defaultRollingRatePerHour = details.defaultRollingRatePerHour() == null
                ? null : Money.round(details.defaultRollingRatePerHour());
        this.defaultWarpingRatePerKg = details.defaultWarpingRatePerKg() == null
                ? null : Money.round(details.defaultWarpingRatePerKg());
    }

    // Replaces all phone numbers. At most one may be primary; when numbers exist but none is primary,
    // the first becomes primary (FR-PARTY-04).
    public void replacePhones(List<PartyPhoneNumber> newPhones) {
        List<PartyPhoneNumber> list = newPhones == null ? List.of() : newPhones;
        long primaries = list.stream().filter(PartyPhoneNumber::isPrimary).count();
        if (primaries > 1) {
            throw new FieldValidationException("phones", "Only one phone number can be primary.");
        }
        List<PartyPhoneNumber> result = new ArrayList<>(list);
        if (primaries == 0 && !result.isEmpty()) {
            result.set(0, result.get(0).asPrimary());
        }
        phones.clear();
        phones.addAll(result);
    }

    // Roles are only ever added (history may reference them). Adding a held role changes nothing.
    public void addRole(PartyRole role) {
        roles.add(role);
    }

    public void deactivate() {
        if (status == PartyStatus.INACTIVE) {
            throw new InvalidStateTransitionException("Party", status, PartyStatus.INACTIVE);
        }
        status = PartyStatus.INACTIVE;
    }

    public void activate() {
        if (status == PartyStatus.ACTIVE) {
            throw new InvalidStateTransitionException("Party", status, PartyStatus.ACTIVE);
        }
        status = PartyStatus.ACTIVE;
    }

    public void updateCustomerTerms(Integer creditPeriodDays) {
        if (!roles.contains(PartyRole.CUSTOMER)) {
            throw new FieldValidationException("customerCreditDays", "Customer credit terms are allowed only for a customer.");
        }
        if (creditPeriodDays != null && (creditPeriodDays < 0 || creditPeriodDays > 365)) {
            throw new FieldValidationException("creditPeriodDays", "Credit period days must be between 0 and 365.");
        }
        this.customerCreditDays = creditPeriodDays;
    }

    // The single check before a party is used in a new transaction (FR-PARTY-11/12).
    public void requireUsableAs(PartyRole role) {
        if (!roles.contains(role)) {
            throw new InvalidPartyRoleException(partyCode, role);
        }
        if (status != PartyStatus.ACTIVE) {
            throw new PartyInactiveException(partyCode);
        }
    }

    public boolean hasRole(PartyRole role) {
        return roles.contains(role);
    }

    public String getPartyCode() {
        return partyCode;
    }

    public String getName() {
        return name;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public String getArea() {
        return area;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getState() {
        return state;
    }

    public String getPincode() {
        return pincode;
    }

    public String getNote() {
        return note;
    }

    public PartyStatus getStatus() {
        return status;
    }

    public Integer getDefaultCreditDays() {
        return defaultCreditDays;
    }

    public Integer getCustomerCreditDays() {
        return customerCreditDays;
    }

    public BigDecimal getDefaultRollingRatePerHour() {
        return defaultRollingRatePerHour;
    }

    public BigDecimal getDefaultWarpingRatePerKg() {
        return defaultWarpingRatePerKg;
    }

    // Read-only, sorted views (the response lists roles alphabetically).
    public List<PartyRole> getRoles() {
        return roles.stream().sorted(java.util.Comparator.comparing(Enum::name)).toList();
    }

    public List<PartyPhoneNumber> getPhones() {
        return List.copyOf(phones);
    }

    // The primary number, or null when the party has none.
    public String primaryPhone() {
        return phones.stream().filter(PartyPhoneNumber::isPrimary).map(PartyPhoneNumber::getPhoneNumber)
                .findFirst().orElse(null);
    }
}
