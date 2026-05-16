package com.czertainly.core.service.v2;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.core.connector.v2.ConnectorDetailDto;
import com.czertainly.api.model.core.connector.v2.ConnectorRequestDto;
import com.czertainly.core.service.ResourceExtensionService;

/**
 * Internal interface for v2 connector operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup.
 * Controller-callable methods are defined in ConnectorExternalService.
 */
public interface ConnectorInternalService extends ResourceExtensionService {

    /**
     * Create a new Connector with status WAITING_FOR_APPROVAL. Used by ConnectorRegistrationService
     * when a new Connector registers itself. Should not be used as replacement for createConnector,
     * as it creates the connector without authorization checks.
     */
    ConnectorDetailDto createNewWaitingConnector(ConnectorRequestDto request) throws ConnectorException, AlreadyExistException, AttributeException, NotFoundException;
}
