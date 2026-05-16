package com.czertainly.core.service.v2;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;
import com.czertainly.core.service.ResourceExtensionService;

import java.util.UUID;

/**
 * Internal interface for v2 compliance profile operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup.
 * Controller-callable methods are defined in ComplianceProfileExternalService.
 */
public interface ComplianceProfileInternalService extends ResourceExtensionService {

    /**
     * Internal lookup of compliance profile by UUID without authorization check.
     */
    NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException;
}
