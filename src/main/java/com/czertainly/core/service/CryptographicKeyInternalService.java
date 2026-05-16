package com.czertainly.core.service;

import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;

import java.security.PublicKey;
import java.util.UUID;

/**
 * Internal interface for Cryptographic Key operations. Extends ResourceExtensionService to allow
 * this service to be used as a resource extension for object lookup and permission evaluation.
 * The controller-callable methods are defined in CryptographicKeyExternalService.
 */
public interface CryptographicKeyInternalService extends ResourceExtensionService {

    /**
     * Function to get the key based on the sha 256 key fingerprint.
     *
     * @param fingerprint SHA 256 fingerprint of the key
     * @return Cryptographic Key UUID
     */
    UUID findKeyByFingerprint(String fingerprint);

    /**
     * Get the key item of specified type based on the cryptographic key.
     *
     * @param key     Cryptographic Key wrapper object
     * @param keyType Key type
     * @return Key Item
     */
    CryptographicKeyItem getKeyItemFromKey(CryptographicKey key, KeyType keyType);

    /**
     * Upload public key of existing certificate.
     *
     * @param name        Name of the cryptographic key
     * @param publicKey   Public Key to be uploaded
     * @param keyLength   Length of the Public Key
     * @param fingerprint Unique fingerprint of the Public Key
     * @return UUID of the uploaded Cryptographic Key
     */
    UUID uploadCertificatePublicKey(String name, PublicKey publicKey, int keyLength, String fingerprint);
}
