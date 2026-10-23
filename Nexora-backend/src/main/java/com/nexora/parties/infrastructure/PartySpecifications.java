package com.nexora.parties.infrastructure;

import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexora.parties.domain.Party;
import com.nexora.parties.domain.PartyRole;
import com.nexora.parties.domain.PartyStatus;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

// Building blocks for the party list filter. Criteria API = always bound parameters, never SQL text built
// from user input (NFR-SEC-10). Each method returns null when its filter is not used (Spring Data skips it).
public final class PartySpecifications {

    // Escape character for LIKE patterns.
    private static final char ESCAPE = '\\';

    private PartySpecifications() {
    }

    public static Specification<Party> hasRole(PartyRole role) {
        return role == null ? null : (root, query, cb) -> cb.isMember(role, root.get("roles"));
    }

    public static Specification<Party> hasStatus(PartyStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    // Case-insensitive substring of the name, the party code or any phone number.
    public static Specification<Party> matches(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            // EXISTS (a phone of this party matches): a subquery, so a party with two matching phones
            // is still returned once and the count stays right
            Subquery<UUID> phoneMatch = query.subquery(UUID.class);
            Root<Party> sameParty = phoneMatch.correlate(root);
            Join<Party, ?> phone = sameParty.join("phones");
            phoneMatch.select(sameParty.get("id"))
                    .where(cb.like(cb.lower(phone.get("phoneNumber")), pattern, ESCAPE));
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("partyCode")), pattern, ESCAPE),
                    cb.exists(phoneMatch));
        };
    }

    // "100%" must match the text 100%, not "100 followed by anything": \, % and _ lose their LIKE meaning.
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
