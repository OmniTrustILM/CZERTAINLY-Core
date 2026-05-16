package com.czertainly.core.service;

import com.czertainly.api.exception.*;
import com.czertainly.api.model.common.attribute.common.MetadataAttribute;
import com.czertainly.api.model.core.certificate.CertificateDetailDto;
import com.czertainly.api.model.core.certificate.CertificateType;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.RaProfile;
import com.czertainly.core.security.authz.SecuredUUID;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificateInternalService extends ResourceExtensionService {

    Certificate getCertificateEntityByContent(String content);

    Certificate getCertificateEntityByFingerprint(String fingerprint) throws NotFoundException;

    Certificate getCertificateEntityByIssuerDnNormalizedAndSerialNumber(String issuerDn, String serialNumber) throws NotFoundException;

    Optional<Certificate> findCertificateEntityByUserUuid(UUID userUuid);

    boolean checkCertificateExistsByFingerprint(String fingerprint);

    Certificate checkCreateCertificate(String certificate) throws AlreadyExistException, CertificateException, NoSuchAlgorithmException;

    CertificateContent checkAddCertificateContent(String fingerprint, String content);

    Certificate createCertificateAtomic(String certificate, boolean assignOwner) throws CertificateException, NoSuchAlgorithmException, NotFoundException;

    void validate(Certificate certificate);

    /**
     * List the available certificates that are associated with the RA Profile
     *
     * @param raProfile Ra Profile entity to search for the certificates
     * @return List of Certificates
     */
    List<Certificate> listCertificatesForRaProfile(RaProfile raProfile);

    /**
     * List the available certificates that are associated with the RA Profile with non-null compliance status
     *
     * @param raProfile Ra Profile entity to search for the certificates
     * @return List of Certificates
     */
    List<Certificate> listCertificatesForRaProfileAndNonNullComplianceStatus(RaProfile raProfile);

    /**
     * Update the Certificate Entity
     *
     * @param certificate Certificate entity to be updated
     */
    void updateCertificateEntity(Certificate certificate);

    /**
     * Function to update status of certificates by scheduled event
     */
    int updateCertificatesStatusScheduled();

    /**
     * Update the user uuid of the certificate in the core database
     *
     * @param certificateUuid UUID of the certificate
     * @param userUuid        UUID of the User
     * @throws NotFoundException
     */
    void updateCertificateUser(UUID certificateUuid, String userUuid) throws NotFoundException;

    /**
     * Remove the user uuid of the certificate in the core database
     *
     * @param userUuid UUID of the User
     */
    void removeCertificateUser(UUID userUuid);

    /**
     * Unassociate the given key from all the certificates.
     * @param keyUuid UUID of the key object or alternative key object to be unassociated
     */
    void clearKeyAssociations(UUID keyUuid);

    /**
     * Unassociate the given keys from all the certificates.
     * @param keyUuids list of UUID of the key objects or alternative key objects to be unassociated
     */
    void bulkClearKeyAssociations(List<UUID> keyUuids);

    /**
     * Function to update the certificate with the keys if known
     * @param keyUuid UUID of the key
     * @param publicKeyFingerprint fingerprint of the public key
     */
    void updateCertificateKeys(UUID keyUuid, String publicKeyFingerprint);

    /**
     * Method to switch RA profile of a Certificate
     * @param uuid          UUID of the certificate
     * @param raProfileUuid UUID of the RA profile to switch to
     */
    void switchRaProfile(SecuredUUID uuid, SecuredUUID raProfileUuid) throws NotFoundException, CertificateOperationException, AttributeException;

    /**
     * Update Subject DN and Issuer DN in certificates when there is a change in code
     * @param oid of RDN to change
     * @param newCode to change
     * @param oldCode previous code to be changed
     */
    void updateCertificateDNs(String oid, String newCode, String oldCode);

    /**
     * Find certificates which are expiring and not renewed and trigger event handling these certificates
     */
    int handleExpiringCertificates();

    /**
     * Function to change the Certificate Entity from CSR to Certificate
     * @param uuid UUID of the entity to be transformed
     * @param certificateData Issued Certificate Data
     * @param meta Metadata of the certificate
     * @return Certificate detail DTO
     */
    CertificateDetailDto issueRequestedCertificate(UUID uuid, String certificateData, List<MetadataAttribute> meta) throws CertificateException, NoSuchAlgorithmException, AlreadyExistException, NotFoundException, AttributeException;
}
