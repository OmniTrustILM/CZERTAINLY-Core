package com.czertainly.core.service;

import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;

import java.util.UUID;

/**
 * Internal interface for TSP Profile operations not exposed to API controllers.
 * Extends ResourceExtensionService to allow this service to be used as a resource extension
 * for object lookup. Controller-callable methods are defined in TspProfileExternalService.
 */
public interface TspProfileInternalService extends ResourceExtensionService {

    NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException;
}
