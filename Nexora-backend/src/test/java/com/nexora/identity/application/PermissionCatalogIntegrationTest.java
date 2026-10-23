package com.nexora.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.nexora.identity.domain.Role;
import com.nexora.identity.infrastructure.RoleRepository;
import com.nexora.shared.security.Permission;
import com.nexora.support.AbstractIntegrationTest;

// The Permission enum (used in @PreAuthorize) and the permissions table (seeded by V5) must be the same list.
// A permission that exists only in code could never be granted; one only in the table is a typo waiting to happen.
class PermissionCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RoleRepository roleRepository;

    @Test
    @DisplayName("FR-AUTH-09 permission enum matches permissions table")
    void permissionEnum_matchesPermissionsTable() {
        List<String> inTable = jdbc.queryForList("select code from permissions", String.class);
        List<String> inCode = Arrays.stream(Permission.values()).map(Enum::name).toList();

        assertThat(inTable).containsExactlyInAnyOrderElementsOf(inCode);
    }

    @Test
    @Transactional(readOnly = true)   // the role's permissions are loaded lazily inside this transaction
    @DisplayName("FR-AUTH-09 owner has every permission and staff is read only")
    void seededRoles_haveTheDocumentedPermissions() {
        Role owner = roleRepository.findByName("OWNER").orElseThrow();
        Role staff = roleRepository.findByName("STAFF").orElseThrow();

        assertThat(owner.getPermissions()).containsExactlyInAnyOrder(Permission.values());
        assertThat(staff.getPermissions()).containsExactlyInAnyOrder(
                Permission.PARTY_VIEW, Permission.INVENTORY_VIEW, Permission.ORDER_VIEW, Permission.PRODUCTION_VIEW);
    }
}
