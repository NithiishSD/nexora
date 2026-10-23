package com.nexora.identity.domain;

import java.util.HashSet;
import java.util.Set;

import com.nexora.shared.domain.AuditableEntity;
import com.nexora.shared.security.Permission;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

// A role is only a named bundle of permissions (OWNER = all, STAFF = read-only). Authorization checks
// permissions, never role names (ADR-014). Roles and their permissions are seeded by migration V5.
@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

    @Column(nullable = false, length = 50, unique = true)
    private String name;

    @Column(length = 255)
    private String description;

    // role_permissions(role_id, permission_code): a plain value collection, stored as the enum name.
    // Lazy by default: loaded only when a use case asks for the permissions (inside its transaction).
    @ElementCollection
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_code", nullable = false, length = 60)
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
        // for JPA only
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    // Read-only view: permissions are changed by migrations, not by application code.
    public Set<Permission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
