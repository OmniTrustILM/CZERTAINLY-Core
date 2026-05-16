package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.RoleRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.*;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.util.List;

public interface RoleManagementExternalService {

    List<RoleDto> listRoles();

    RoleDetailDto getRole(String roleUuid);

    RoleDetailDto createRole(RoleRequestDto request) throws NotFoundException, AttributeException;

    RoleDetailDto updateRole(String roleUuid, RoleRequestDto request) throws NotFoundException, AttributeException;

    void deleteRole(String roleUuid);

    SubjectPermissionsDto getRolePermissions(String roleUuid);

    SubjectPermissionsDto addPermissions(String roleUuid, RolePermissionsRequestDto request);

    ResourcePermissionsDto getRoleResourcePermission(String roleUuid, String resourceUuid);

    List<ObjectPermissionsDto> getResourcePermissionObjects(String roleUuid, String resourceUuid);

    void addResourcePermissionObjects(String roleUuid, String resourceUuid, List<ObjectPermissionsRequestDto> request);

    void updateResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid, ObjectPermissionsRequestDto request);

    void removeResourcePermissionObjects(String roleUuid, String resourceUuid, String objectUuid);

    List<UserDto> getRoleUsers(String roleUuid);

    RoleDetailDto updateUsers(String roleUuid, List<String> userUuids);

    NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException;

    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination);

    void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException;
}
