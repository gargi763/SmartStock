package com.smartstock.app.service;

import com.smartstock.app.domain.Role;
import com.smartstock.app.domain.User;
import com.smartstock.app.dto.AdminUserResponse;
import com.smartstock.app.exception.ResourceNotFoundException;
import com.smartstock.app.exception.UserRoleChangeNotAllowedException;
import com.smartstock.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AdminUserService.class)
class AdminUserServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminUserService adminUserService;

    private User administrator;
    private User ordinaryUser;

    @BeforeEach
    void setUp() {
        administrator = userRepository.save(User.builder()
                .username("admin")
                .email("admin@example.test")
                .password("encoded-secret")
                .roles(Set.of(Role.ADMIN))
                .build());
        ordinaryUser = userRepository.save(User.builder()
                .username("alice")
                .email("alice@example.test")
                .password("encoded-secret")
                .roles(Set.of(Role.USER))
                .build());
    }

    @Test
    void listAndGetUsers_returnSafeFieldsWithoutCredentialData() {
        List<AdminUserResponse> users = adminUserService.getUsers();
        AdminUserResponse response = adminUserService.getUser(ordinaryUser.getId());

        assertThat(users).hasSize(2);
        assertThat(response.id()).isEqualTo(ordinaryUser.getId());
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.test");
        assertThat(response.roles()).containsExactly(Role.USER);
        assertThat(response.toString()).doesNotContain("encoded-secret");
    }

    @Test
    void updateUserRole_changesOnlyTheSelectedUsersRole() {
        AdminUserResponse updated = adminUserService.updateUserRole(
                ordinaryUser.getId(), Role.ADMIN, "admin");

        assertThat(updated.roles()).containsExactly(Role.ADMIN);
        assertThat(userRepository.findById(ordinaryUser.getId()).orElseThrow().getRoles())
                .containsExactly(Role.ADMIN);
        assertThat(userRepository.findById(administrator.getId()).orElseThrow().getRoles())
                .containsExactly(Role.ADMIN);

        updated = adminUserService.updateUserRole(ordinaryUser.getId(), Role.USER, "admin");
        assertThat(updated.roles()).containsExactly(Role.USER);
    }

    @Test
    void updateUserRole_preventsAdministratorFromChangingOwnRole() {
        assertThatThrownBy(() -> adminUserService.updateUserRole(
                administrator.getId(), Role.USER, "admin"))
                .isInstanceOf(UserRoleChangeNotAllowedException.class);

        assertThat(userRepository.findById(administrator.getId()).orElseThrow().getRoles())
                .containsExactly(Role.ADMIN);
    }

    @Test
    void invalidIdsAndNullRolesAreRejected() {
        assertThatThrownBy(() -> adminUserService.getUser(999L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adminUserService.getUser(0L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adminUserService.updateUserRole(999L, Role.USER, "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> adminUserService.updateUserRole(ordinaryUser.getId(), null, "admin"))
                .isInstanceOf(UserRoleChangeNotAllowedException.class);
    }
}
