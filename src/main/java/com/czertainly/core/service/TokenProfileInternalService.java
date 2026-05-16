package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.security.authz.SecuredUUID;

/**
 * Internal interface for Token Profile operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in TokenProfileExternalService.
 */
public interface TokenProfileInternalService extends ResourceExtensionService {

    /**
     * Get the Token Profile entity without authorization check. Internal use only.
     *
     * @param uuid UUID of the Token Profile
     * @return Token Profile entity
     * @throws NotFoundException when the token profile is not found
     */
    TokenProfile getTokenProfileEntity(SecuredUUID uuid) throws NotFoundException;
}
