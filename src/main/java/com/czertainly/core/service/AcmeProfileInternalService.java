package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;

import java.util.UUID;

/**
 * Internal interface for ACME Profile operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup.
 * The controller-callable methods are defined in AcmeProfileExternalService.
 */
public interface AcmeProfileInternalService extends ResourceExtensionService {

    NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException;

}
