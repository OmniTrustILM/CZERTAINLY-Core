package com.czertainly.core.service;

import com.czertainly.api.clients.ApiClientConnectorInfo;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.connector.secrets.SecretOperationRequest;

import java.util.UUID;

/**
 * Internal interface for VaultInstance operations not exposed to external callers.
 * Controller-callable methods are defined in VaultInstanceExternalService.
 */
public interface VaultInstanceInternalService {

    void loadAttributesForSecretOperation(ApiClientConnectorInfo connector, UUID vaultInstanceUuid, UUID vaultProfileUuid, SecretOperationRequest secretOperationRequest) throws NotFoundException, ConnectorException, AttributeException;
}
