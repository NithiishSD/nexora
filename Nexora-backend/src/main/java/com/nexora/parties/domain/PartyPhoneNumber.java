package com.nexora.parties.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// One phone number of a party (a row of party_phone_numbers). It has no life of its own: the list is
// always replaced as a whole, so it is a value (embeddable) owned by the Party, not a separate entity.
// Not unique across parties: family members share numbers.
@Embeddable
public class PartyPhoneNumber {

    // Generated here (not by the database) so the id is known immediately, e.g. in the create response.
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "phone_number", nullable = false, length = 30)
    private String phoneNumber;

    @Column(name = "label", nullable = false, length = 50)
    private String label;                    // "mobile", "WhatsApp", ...

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    protected PartyPhoneNumber() {
        // for JPA only
    }

    public static PartyPhoneNumber of(String phoneNumber, String label, boolean primary) {
        PartyPhoneNumber phone = new PartyPhoneNumber();
        phone.id = UUID.randomUUID();
        phone.phoneNumber = phoneNumber.trim();
        phone.label = label.trim();
        phone.primary = primary;
        return phone;
    }

    // A copy marked as primary (used when no number was flagged and the first one becomes primary).
    PartyPhoneNumber asPrimary() {
        return of(phoneNumber, label, true);
    }

    public UUID getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPrimary() {
        return primary;
    }
}
