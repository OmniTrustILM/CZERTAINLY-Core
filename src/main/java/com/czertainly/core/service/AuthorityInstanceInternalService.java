package com.czertainly.core.service;

/**
 * Internal interface for authority instance operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in AuthorityInstanceExternalService.
 */
public interface AuthorityInstanceInternalService extends ResourceExtensionService {
}
