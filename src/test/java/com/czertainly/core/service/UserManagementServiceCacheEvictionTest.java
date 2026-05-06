package com.czertainly.core.service;

import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.core.security.authn.client.AuthenticationCache;
import com.czertainly.core.security.authn.client.UserManagementApiClient;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.SessionTableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

class UserManagementServiceCacheEvictionTest extends BaseSpringBootTest {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserManagementApiClient userManagementApiClient;

    @MockitoBean
    private AuthenticationCache authenticationCache;

    @BeforeEach
    void setupSessionTables() {
        SessionTableHelper.createSessionTables(jdbcTemplate);
    }

    @Test
    void updateUser_evictsUserCache() throws Exception {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateUser(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        UpdateUserRequestDto request = new UpdateUserRequestDto();
        request.setCustomAttributes(List.of());

        // when
        userManagementService.updateUser(userUuid, request);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void updateUserInternal_evictsUserCache() throws Exception {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateUser(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateUserInternal(userUuid, new UpdateUserRequestDto(), "", "");

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void deleteUser_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();

        // when
        userManagementService.deleteUser(userUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void disableUser_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.disableUser(userUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.disableUser(userUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void updateRoles_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateRoles(Mockito.eq(userUuid), Mockito.any()))
                .thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateRoles(userUuid, List.of());

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void updateRole_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        String roleUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.updateRole(userUuid, roleUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.updateRole(userUuid, roleUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    @Test
    void removeRole_evictsUserCache() {
        // given
        String userUuid = UUID.randomUUID().toString();
        String roleUuid = UUID.randomUUID().toString();
        Mockito.when(userManagementApiClient.removeRole(userUuid, roleUuid)).thenReturn(userDetailDto(userUuid));

        // when
        userManagementService.removeRole(userUuid, roleUuid);

        // then
        Mockito.verify(authenticationCache).evictByUserUuid(userUuid);
    }

    private static UserDetailDto userDetailDto(String uuid) {
        UserDetailDto dto = new UserDetailDto();
        dto.setUuid(uuid);
        dto.setUsername("user-" + uuid);
        return dto;
    }
}
