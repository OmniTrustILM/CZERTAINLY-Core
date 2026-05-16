package com.czertainly.core.service;

import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.model.client.attribute.RequestAttribute;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.UUID;

/**
 * Internal interface for Cryptographic Operation operations.
 * The controller-callable methods are defined in CryptographicOperationExternalService.
 */
public interface CryptographicOperationInternalService {

    /**
     * Generate the CSR with the key and token profile and CSR parameters.
     * Internal use only — not exposed through the external controller API.
     *
     * @param keyUuid             UUID of the cryptographic key
     * @param tokenProfileUuid    UUID of the token profile
     * @param principal           X500 Principal
     * @param signatureAttributes Signature attributes
     * @param altKeyUUid          UUID of the alternative key (for dual signing)
     * @param altTokenProfileUuid UUID of the alternative token profile
     * @param altSignatureAttributes Signature attributes for alternative key
     * @return Base64 encoded CSR string
     * @throws NotFoundException        when the key or token profile is not found
     * @throws NoSuchAlgorithmException when the algorithm is invalid
     * @throws InvalidKeySpecException  when the key is invalid
     * @throws IOException              when there are issues with writing the key data as string
     */
    String generateCsr(
            UUID keyUuid,
            UUID tokenProfileUuid,
            X500Principal principal,
            List<RequestAttribute> signatureAttributes,
            UUID altKeyUUid,
            UUID altTokenProfileUuid,
            List<RequestAttribute> altSignatureAttributes
    ) throws NotFoundException, NoSuchAlgorithmException, InvalidKeySpecException, IOException, AttributeException;
}
