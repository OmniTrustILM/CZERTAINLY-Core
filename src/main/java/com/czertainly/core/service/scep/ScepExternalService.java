package com.czertainly.core.service.scep;

import com.czertainly.api.exception.ScepException;
import org.springframework.http.ResponseEntity;

public interface ScepExternalService {

    ResponseEntity<Object> handleGet(String scepProfileName, String operation, String message) throws ScepException;

    ResponseEntity<Object> handlePost(String scepProfileName, String operation, byte[] message) throws ScepException;
}
