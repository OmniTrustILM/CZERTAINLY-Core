package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.auth.AddUserRequestDto;
import com.czertainly.api.model.client.auth.UpdateUserRequestDto;
import com.czertainly.api.model.client.auth.UserIdentificationRequestDto;
import com.czertainly.api.model.client.certificate.SearchFilterRequestDto;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.api.model.core.auth.RoleDto;
import com.czertainly.api.model.core.auth.SubjectPermissionsDto;
import com.czertainly.api.model.core.auth.UserDetailDto;
import com.czertainly.api.model.core.auth.UserDto;
import com.czertainly.api.model.core.scheduler.PaginationRequestDto;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;

import java.security.cert.CertificateException;
import java.util.List;

public interface UserManagementExternalService {

    List<UserDto> listUsers();

    UserDetailDto getUser(String userUuid) throws NotFoundException;

    UserDetailDto createUser(AddUserRequestDto request) throws CertificateException, NotFoundException, AttributeException;

    UserDetailDto updateUser(String userUuid, UpdateUserRequestDto request) throws NotFoundException, CertificateException, AttributeException;

    void deleteUser(String userUuid);

    UserDetailDto updateRoles(String userUuid, List<String> roleUuids);

    UserDetailDto updateRole(String userUuid, String roleUuid);

    SubjectPermissionsDto getPermissions(String userUuid);

    UserDetailDto enableUser(String userUuid);

    UserDetailDto disableUser(String userUuid);

    List<RoleDto> getUserRoles(String userUuid);

    UserDetailDto removeRole(String userUuid, String roleUuid);

    UserDetailDto identifyUser(UserIdentificationRequestDto request) throws NotFoundException, CertificateException;

    NameAndUuidDto getResourceObjectExternal(SecuredUUID objectUuid) throws NotFoundException;

    List<NameAndUuidDto> listResourceObjects(SecurityFilter filter, List<SearchFilterRequestDto> filters, PaginationRequestDto pagination);

    void evaluatePermissionChain(SecuredUUID uuid) throws NotFoundException;
}
