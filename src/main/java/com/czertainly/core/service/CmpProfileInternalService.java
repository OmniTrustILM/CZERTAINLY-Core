package com.czertainly.core.service;

/**
 * Internal interface for CMP Profile operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in CmpProfileExternalService.
 */
public interface CmpProfileInternalService extends ResourceExtensionService {
}
