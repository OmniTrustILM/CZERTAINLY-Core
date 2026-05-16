package com.czertainly.core.service;

/**
 * Internal interface for connector operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in ConnectorExternalService.
 */
public interface ConnectorInternalService extends ResourceExtensionService {
}
