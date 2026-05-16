package com.czertainly.core.service;

import com.czertainly.api.exception.CbomRepositoryException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.common.NameAndUuidDto;

import java.util.UUID;

/**
 * Internal interface for CBOM operations not exposed to API controllers.
 * Extends ResourceExtensionService to allow this service to be used as a resource extension
 * for object lookup. Controller-callable methods are defined in CbomExternalService.
 */
public interface CbomInternalService extends ResourceExtensionService {

    NameAndUuidDto getResourceObjectInternal(UUID objectUuid) throws NotFoundException;

    /**
     * Synchronize CBOMs from the CBOM repository. This version is intended for use
     * by scheduled jobs where no authorization context is available.
     *
     * @return A string message indicating the result of the synchronization process
     * @throws CbomRepositoryException if there are problems accessing the CBOM repository
     */
    String sync() throws CbomRepositoryException;

    /**
     * Check whether the CBOM repository client configuration is present.
     *
     * @return {@code true} if the CBOM repository base URL/client configuration is present and the client is considered configured,
     *         {@code false} otherwise
     */
    boolean isCbomRepositoryClientConfigured();
}
