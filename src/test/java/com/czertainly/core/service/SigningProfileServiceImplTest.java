package com.czertainly.core.service;

import com.czertainly.api.exception.AlreadyExistException;
import com.czertainly.api.exception.AttributeException;
import com.czertainly.api.exception.ConnectorException;
import com.czertainly.api.exception.NotFoundException;
import com.czertainly.api.exception.ValidationError;
import com.czertainly.api.exception.ValidationException;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.client.attribute.RequestAttributeV3;
import com.czertainly.api.model.client.attribute.ResponseAttribute;
import com.czertainly.api.model.client.attribute.ResponseAttributeV2;
import com.czertainly.api.model.client.attribute.ResponseAttributeV3;
import com.czertainly.api.model.client.certificate.SearchRequestDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileListDto;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.SimplifiedSigningProfileDto;
import com.czertainly.api.model.client.signing.profile.scheme.DelegatedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.ManagedSigningType;
import com.czertainly.api.model.client.signing.profile.scheme.OneTimeKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.SigningScheme;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.ContentSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.RawSigningWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.SigningWorkflowType;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.WorkflowRequestDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationDto;
import com.czertainly.api.model.client.signing.timequality.TimeQualityConfigurationRequestDto;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.api.model.common.BulkActionMessageDto;
import com.czertainly.api.model.common.PaginationResponseDto;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.common.AttributeType;
import com.czertainly.api.model.common.attribute.common.properties.CustomAttributeProperties;
import com.czertainly.api.model.common.attribute.v3.CustomAttributeV3;
import com.czertainly.api.model.common.attribute.v3.content.StringAttributeContentV3;
import com.czertainly.api.model.core.auth.Resource;
import com.czertainly.core.dao.entity.AttributeDefinition;
import com.czertainly.core.dao.entity.AttributeRelation;
import com.czertainly.core.dao.entity.signing.SigningProfileVersion;
import com.czertainly.core.dao.repository.AttributeDefinitionRepository;
import com.czertainly.core.dao.repository.AttributeRelationRepository;
import com.czertainly.api.model.common.attribute.common.properties.DataAttributeProperties;
import com.czertainly.api.model.common.attribute.v2.DataAttributeV2;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.common.enums.cryptography.RsaSignatureScheme;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;
import com.czertainly.api.model.core.signing.SigningProtocol;
import com.czertainly.api.model.client.connector.v2.ConnectorInterface;
import com.czertainly.api.model.client.connector.v2.ConnectorVersion;
import com.czertainly.api.model.client.connector.v2.FeatureFlag;
import com.czertainly.core.dao.entity.ConnectorInterfaceEntity;
import com.czertainly.core.dao.repository.ConnectorInterfaceRepository;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.attribute.engine.AttributeEngine;
import com.czertainly.core.attribute.engine.AttributeOperation;
import com.czertainly.core.attribute.engine.records.ObjectAttributeContentInfo;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.entity.signing.SigningRecord;
import com.czertainly.core.dao.entity.signing.SigningProfile;
import com.czertainly.core.dao.entity.signing.TspProfile;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningRecordRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileRepository;
import com.czertainly.core.dao.repository.signing.SigningProfileVersionRepository;
import com.czertainly.core.dao.repository.signing.TspProfileRepository;
import com.czertainly.core.model.signing.workflow.ManagedTimestampingWorkflow;
import com.czertainly.core.model.signing.timequality.ExplicitTimeQualityConfiguration;
import com.czertainly.core.model.signing.timequality.TimeQualityConfigurationModel;
import com.czertainly.core.model.signing.SigningProfileModel;
import com.czertainly.core.model.signing.scheme.StaticKeyManagedSigning;
import com.czertainly.core.security.authz.SecuredUUID;
import com.czertainly.core.security.authz.SecurityFilter;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateTestUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SigningProfileServiceImplTest extends BaseSpringBootTest {

    private static final String CUSTOM_ATTR_UUID = "a1b2c3d4-0001-0002-0003-000000000003";
    private static final String CUSTOM_ATTR_NAME = "signingProfileTestAttribute";
    private static final String MISSING_UUID = "00000000-0000-0000-0000-000000000001";

    @Autowired
    private SigningProfileService signingProfileService;

    @Autowired
    private AttributeEngine attributeEngine;

    @Autowired
    private TimeQualityConfigurationService timeQualityConfigurationService;

    @Autowired
    private ConnectorRepository connectorRepository;

    @Autowired
    private TokenInstanceReferenceRepository tokenInstanceReferenceRepository;

    @Autowired
    private TokenProfileRepository tokenProfileRepository;

    @Autowired
    private CryptographicKeyRepository cryptographicKeyRepository;

    @Autowired
    private CryptographicKeyItemRepository cryptographicKeyItemRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private SigningProfileRepository signingProfileRepository;

    @Autowired
    private SigningProfileVersionRepository signingProfileVersionRepository;

    @Autowired
    private SigningRecordRepository signingRecordRepository;

    @Autowired
    private TspProfileRepository tspRepository;

    @Autowired
    private AttributeDefinitionRepository attributeDefinitionRepository;

    @Autowired
    private AttributeRelationRepository attributeRelationRepository;

    @Autowired
    private ConnectorInterfaceRepository connectorInterfaceRepository;

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NotFound {

        Stream<Arguments> notFoundOperations() {
            SecuredUUID missing = SecuredUUID.fromString(MISSING_UUID);
            return Stream.of(
                    Arguments.of(
                            (Executable) () -> signingProfileService.getSigningProfile(missing, null),
                            "getSigningProfile(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.updateSigningProfile(
                                    missing, buildDelegatedRawRequest("x")),
                            "updateSigningProfile(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.deleteSigningProfile(missing),
                            "deleteSigningProfile(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.enableSigningProfile(missing),
                            "enableSigningProfile(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.disableSigningProfile(missing),
                            "disableSigningProfile(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> {
                                TspProfile tsp = new TspProfile();
                                tsp.setName("tsp-for-notfound-test");
                                tsp = tspRepository.save(tsp);
                                signingProfileService.activateTsp(missing, tsp.getSecuredUuid());
                            },
                            "activateTsp(missingProfile)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.deactivateTsp(missing),
                            "deactivateTsp(missingUuid)"
                    ),
                    Arguments.of(
                            (Executable) () -> signingProfileService.getManagedTimestampingProfileModel("no-such-profile"),
                            "getManagedTimestampingProfileModel(nonExistentName)"
                    )
            );
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("notFoundOperations")
        void operation_missingProfile_throwsNotFoundException(Executable op, String displayName) {
            // given: a profile UUID that does not exist in the database
            // when/then: calling any service operation with that UUID raises NotFoundException
            assertThrows(NotFoundException.class, op);
        }
    }

    /**
     * A signing profile saved directly via repository, used as pre-existing data in tests.
     */
    private SigningProfile savedProfile;

    /**
     * A token profile used as an FK reference in static-key managed signing scheme requests.
     */
    private TokenProfile tokenProfile;

    /**
     * A CryptographicKey backed by an MLDSA key item (empty signing operation attribute definitions).
     * Used for generic static-key scheme tests that do not exercise signing operation attributes.
     */
    private CryptographicKey cryptographicKey;

    /**
     * A CryptographicKey backed by an RSA key item (RSA signing operation attribute definitions).
     * Used for tests that specifically exercise signing operation attribute storage and retrieval.
     */
    private CryptographicKey rsaCryptographicKey;

    /**
     * A Certificate associated with {@link #cryptographicKey} (MLDSA key).
     * Satisfies all conditions of constructQueryDigitalSigningCertAcceptable:
     * not archived, state=ISSUED, validationStatus=VALID, key has a private key that is ACTIVE
     * with SIGN usage, and the associated key has a Token Profile assigned.
     */
    private Certificate certificate;

    /**
     * A Certificate associated with {@link #rsaCryptographicKey} (RSA key).
     * Satisfies the same conditions as {@link #certificate}.
     */
    private Certificate rsaCertificate;

    /**
     * A Certificate specifically configured for TIMESTAMPING workflow type.
     * Contains the id-kp-timeStamping EKU and is marked as critical.
     */
    private Certificate tsaCertificate;

    /**
     * A Connector used as the signature formatter connector in CONTENT_SIGNING and TIMESTAMPING workflow tests
     * that do not exercise formatter attribute persistence specifically.
     */
    private Connector formatterConnector;

    /**
     * WireMock server that backs every formatter connector URL created via {@link #createFormatterConnector}.
     */
    private WireMockServer mockServer;

    @BeforeEach
    void setUp() throws CertificateException, IOException, NoSuchAlgorithmException, OperatorCreationException {
        mockServer = new WireMockServer(0);
        mockServer.start();
        WireMock.configureFor("localhost", mockServer.port());
        mockServer.stubFor(
                WireMock.get(WireMock.urlPathMatching(".*/v1/signatureProvider/formatting/attributes"))
                        .willReturn(WireMock.okJson("[]"))
        );

        savedProfile = new SigningProfile();
        savedProfile.setName("existing-signing-profile");
        savedProfile.setDescription("Existing profile description");
        savedProfile.setEnabled(false);
        savedProfile.setSigningScheme(SigningScheme.DELEGATED);
        savedProfile.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        savedProfile.setLatestVersion(1);
        savedProfile = signingProfileRepository.save(savedProfile);

        SigningProfileVersion savedProfileV1 = new SigningProfileVersion();
        savedProfileV1.setSigningProfile(savedProfile);
        savedProfileV1.setVersion(1);
        savedProfileV1.setSigningScheme(SigningScheme.DELEGATED);
        savedProfileV1.setWorkflowType(SigningWorkflowType.RAW_SIGNING);
        signingProfileVersionRepository.save(savedProfileV1);

        // Shared token instance infrastructure required by the static-key managed scheme
        Connector connector = new Connector();
        connector.setName("cryptography-connector");
        connector.setUrl("http://cryptography-connector");
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        TokenInstanceReference tokenInstanceRef = new TokenInstanceReference();
        tokenInstanceRef.setName("test-token-instance");
        tokenInstanceRef.setTokenInstanceUuid(UUID.randomUUID().toString());
        tokenInstanceRef.setConnector(connector);
        tokenInstanceRef.setStatus(TokenInstanceStatus.CONNECTED);
        tokenInstanceRef = tokenInstanceReferenceRepository.saveAndFlush(tokenInstanceRef);

        tokenProfile = new TokenProfile();
        tokenProfile.setName("test-token-profile");
        tokenProfile.setTokenInstanceReference(tokenInstanceRef);
        tokenProfile.setEnabled(true);
        tokenProfile.setTokenInstanceName("test-token-instance");
        tokenProfile = tokenProfileRepository.saveAndFlush(tokenProfile);

        // MLDSA key — produces empty attribute definitions; used by generic scheme tests
        cryptographicKey = new CryptographicKey();
        cryptographicKey.setName("test-key-mldsa");
        cryptographicKey.setTokenProfile(tokenProfile);
        cryptographicKey.setTokenInstanceReference(tokenInstanceRef);
        cryptographicKey = cryptographicKeyRepository.saveAndFlush(cryptographicKey);

        CryptographicKeyItem mldsaKeyItem = new CryptographicKeyItem();
        mldsaKeyItem.setKey(cryptographicKey);
        mldsaKeyItem.setKeyUuid(cryptographicKey.getUuid());
        mldsaKeyItem.setType(KeyType.PRIVATE_KEY);
        mldsaKeyItem.setState(KeyState.ACTIVE);
        mldsaKeyItem.setEnabled(true);
        mldsaKeyItem.setKeyAlgorithm(KeyAlgorithm.MLDSA);
        mldsaKeyItem.setLength(2048);
        mldsaKeyItem.setUsage(List.of(KeyUsage.SIGN));
        mldsaKeyItem = cryptographicKeyItemRepository.saveAndFlush(mldsaKeyItem);
        mldsaKeyItem.setKeyReferenceUuid(mldsaKeyItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(mldsaKeyItem);

        // Certificate associated with the MLDSA key; satisfies constructQueryDigitalSigningCertAcceptable conditions
        certificate = new Certificate();
        certificate.setKey(cryptographicKey);
        certificate.setState(CertificateState.ISSUED);
        certificate.setValidationStatus(CertificateValidationStatus.VALID);
        certificate = certificateRepository.saveAndFlush(certificate);
        attachSelfSignedContent(certificate);

        // RSA key — produces RSA attribute definitions; used by attribute-persistence tests
        rsaCryptographicKey = new CryptographicKey();
        rsaCryptographicKey.setName("test-key-rsa");
        rsaCryptographicKey.setTokenProfile(tokenProfile);
        rsaCryptographicKey.setTokenInstanceReference(tokenInstanceRef);
        rsaCryptographicKey = cryptographicKeyRepository.saveAndFlush(rsaCryptographicKey);

        CryptographicKeyItem rsaKeyItem = new CryptographicKeyItem();
        rsaKeyItem.setKey(rsaCryptographicKey);
        rsaKeyItem.setKeyUuid(rsaCryptographicKey.getUuid());
        rsaKeyItem.setType(KeyType.PRIVATE_KEY);
        rsaKeyItem.setState(KeyState.ACTIVE);
        rsaKeyItem.setEnabled(true);
        rsaKeyItem.setKeyAlgorithm(KeyAlgorithm.RSA);
        rsaKeyItem.setLength(2048);
        rsaKeyItem.setUsage(List.of(KeyUsage.SIGN));
        rsaKeyItem = cryptographicKeyItemRepository.saveAndFlush(rsaKeyItem);
        rsaKeyItem.setKeyReferenceUuid(rsaKeyItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(rsaKeyItem);

        // Certificate associated with the RSA key; satisfies constructQueryDigitalSigningCertAcceptable conditions
        rsaCertificate = new Certificate();
        rsaCertificate.setKey(rsaCryptographicKey);
        rsaCertificate.setState(CertificateState.ISSUED);
        rsaCertificate.setValidationStatus(CertificateValidationStatus.VALID);
        rsaCertificate = certificateRepository.saveAndFlush(rsaCertificate);
        attachSelfSignedContent(rsaCertificate);

        // Certificate specifically configured for TIMESTAMPING; satisfies RFC 3161 requirements
        tsaCertificate = new Certificate();
        tsaCertificate.setKey(rsaCryptographicKey);
        tsaCertificate.setState(CertificateState.ISSUED);
        tsaCertificate.setValidationStatus(CertificateValidationStatus.VALID);
        tsaCertificate.setExtendedKeyUsage(MetaDefinitions.serializeArrayString(List.of(SystemOid.TIME_STAMPING.getOid())));
        tsaCertificate.setExtendedKeyUsageCritical(true);
        tsaCertificate = certificateRepository.saveAndFlush(tsaCertificate);
        attachSelfSignedContent(tsaCertificate);

        formatterConnector = createFormatterConnector("default-formatter-connector");

        // Register a custom attribute available for Signing Profile resources
        CustomAttributeV3 attrDef = new CustomAttributeV3();
        attrDef.setUuid(CUSTOM_ATTR_UUID);
        attrDef.setName(CUSTOM_ATTR_NAME);
        attrDef.setDescription("test custom attribute for signing profile");
        attrDef.setContentType(AttributeContentType.STRING);
        CustomAttributeProperties props = new CustomAttributeProperties();
        props.setReadOnly(false);
        props.setRequired(false);
        attrDef.setProperties(props);

        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setUuid(UUID.fromString(CUSTOM_ATTR_UUID));
        attributeDefinition.setName(CUSTOM_ATTR_NAME);
        attributeDefinition.setAttributeUuid(UUID.fromString(CUSTOM_ATTR_UUID));
        attributeDefinition.setContentType(AttributeContentType.STRING);
        attributeDefinition.setLabel(CUSTOM_ATTR_NAME);
        attributeDefinition.setType(AttributeType.CUSTOM);
        attributeDefinition.setDefinition(attrDef);
        attributeDefinition.setEnabled(true);
        attributeDefinition.setVersion(3);
        attributeDefinitionRepository.save(attributeDefinition);

        AttributeRelation attributeRelation = new AttributeRelation();
        attributeRelation.setResource(Resource.SIGNING_PROFILE);
        attributeRelation.setAttributeDefinitionUuid(attributeDefinition.getUuid());
        attributeRelationRepository.save(attributeRelation);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop();
    }

    @Nested
    class ListTests {

        @Test
        void returnsExistingEntries() {
            // given: one profile in the database (savedProfile from setUp)
            SearchRequestDto request = new SearchRequestDto();

            // when
            PaginationResponseDto<SigningProfileListDto> response =
                    signingProfileService.listSigningProfiles(request, SecurityFilter.create());

            // then
            assertNotNull(response);
            assertEquals(1, response.getTotalItems());
            SigningProfileListDto listed = response.getItems().getFirst();
            assertEquals(savedProfile.getUuid().toString(), listed.getUuid());
            assertEquals(savedProfile.getName(), listed.getName());
            assertEquals(savedProfile.getDescription(), listed.getDescription());
            assertEquals(SigningWorkflowType.RAW_SIGNING, listed.getSigningWorkflowType());
            assertFalse(listed.isEnabled());
        }

        @Test
        void emptyWhenNoneExist() {
            // given: no profiles (delete the one from setUp)
            signingProfileService.bulkDeleteSigningProfiles(List.of(savedProfile.getSecuredUuid()));

            // when
            PaginationResponseDto<SigningProfileListDto> response =
                    signingProfileService.listSigningProfiles(new SearchRequestDto(), SecurityFilter.create());

            // then
            assertNotNull(response);
            assertEquals(0, response.getTotalItems());
            assertTrue(response.getItems().isEmpty());
        }

        @Test
        void multipleProfilesWithDifferentWorkflowTypes()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: 3 profiles with different workflow types
            signingProfileService.createSigningProfile(buildDelegatedContentRequest("content-profile"));
            signingProfileService.createSigningProfile(buildDelegatedTimestampingRequest("timestamping-profile"));

            // when
            PaginationResponseDto<SigningProfileListDto> response =
                    signingProfileService.listSigningProfiles(new SearchRequestDto(), SecurityFilter.create());

            // then
            assertEquals(3, response.getTotalItems());
            List<SigningWorkflowType> returnedTypes = response.getItems().stream()
                    .map(SigningProfileListDto::getSigningWorkflowType).toList();
            assertTrue(returnedTypes.contains(SigningWorkflowType.RAW_SIGNING));
            assertTrue(returnedTypes.contains(SigningWorkflowType.CONTENT_SIGNING));
            assertTrue(returnedTypes.contains(SigningWorkflowType.TIMESTAMPING));
        }
    }

    @Nested
    class GetTests {

        @Test
        void returnsCorrectDto() throws NotFoundException {
            // given: savedProfile from setUp
            // when
            SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

            // then
            assertNotNull(dto);
            assertEquals(savedProfile.getUuid().toString(), dto.getUuid());
            assertEquals(savedProfile.getName(), dto.getName());
            assertEquals(savedProfile.getDescription(), dto.getDescription());
            assertFalse(dto.isEnabled());
            assertEquals(1, dto.getVersion());
        }

        @Test
        void specificVersion_returnsSnapshotData()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a profile created via service (creates version 1 snapshot)
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("profile-for-version-get"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            // when: get with explicit version=1
            SigningProfileDto dto = signingProfileService.getSigningProfile(profileUuid, 1);

            // then
            assertNotNull(dto);
            assertEquals(1, dto.getVersion());
            assertNotNull(dto.getSigningScheme());
            assertEquals(SigningScheme.DELEGATED, dto.getSigningScheme().getSigningScheme());
            assertNotNull(dto.getWorkflow());
            assertEquals(SigningWorkflowType.RAW_SIGNING, dto.getWorkflow().getType());
        }

        @Test
        void afterVersionBump_oldVersionPreservesOriginalWorkflowType()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: profile at v1 (RAW_SIGNING), then bumped to v2 (CONTENT_SIGNING)
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("profile-history"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
            createSigningRecordFor(reloadProfile(UUID.fromString(created.getUuid())), 1);
            signingProfileService.updateSigningProfile(profileUuid, buildDelegatedContentRequest("profile-history"));

            // when: fetch version 1 and latest
            SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
            SigningProfileDto latest = signingProfileService.getSigningProfile(profileUuid, null);

            // then
            assertEquals(1, v1.getVersion());
            assertEquals(SigningWorkflowType.RAW_SIGNING, v1.getWorkflow().getType());
            assertEquals(2, latest.getVersion());
            assertEquals(SigningWorkflowType.CONTENT_SIGNING, latest.getWorkflow().getType());
        }

        @Test
        void noProtocolsLinked_enabledProtocolsIsEmpty() throws NotFoundException {
            // given: savedProfile has no TSP linked
            // when
            SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

            // then
            assertNotNull(dto.getEnabledProtocols());
            assertTrue(dto.getEnabledProtocols().isEmpty());
        }

        @Test
        void withTspLinked_enabledProtocolsContainsTsp() throws NotFoundException {
            // given: link a TSP profile
            TspProfile tspProfile = new TspProfile();
            tspProfile.setName("tsp-for-dto-test");
            tspProfile = tspRepository.save(tspProfile);
            savedProfile.setTspProfile(tspProfile);
            signingProfileRepository.save(savedProfile);

            // when
            SigningProfileDto dto = signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null);

            // then
            assertNotNull(dto.getEnabledProtocols());
            assertTrue(dto.getEnabledProtocols().contains(SigningProtocol.TSP));
        }

        @Test
        void nonExistentVersion_throwsNotFoundException()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a valid profile created via service
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("profile-for-version-notfound"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            // when/then: requesting a non-existent version throws NotFoundException
            assertThrows(NotFoundException.class,
                    () -> signingProfileService.getSigningProfile(profileUuid, 99));
        }
    }

    @Nested
    class CreateScheme {

        @Test
        void delegatedRawSigning_assertDtoAndDbEntity()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileRequestDto request = buildDelegatedRawRequest("new-delegated-profile");

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then: DTO fields
            assertNotNull(dto);
            assertNotNull(dto.getUuid());
            assertEquals("new-delegated-profile", dto.getName());
            assertFalse(dto.isEnabled());
            assertEquals(1, dto.getVersion());
            assertNotNull(dto.getSigningScheme());
            assertNotNull(dto.getWorkflow());

            // then: DB entity
            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
            assertEquals(SigningWorkflowType.RAW_SIGNING, entity.getWorkflowType());
            assertEquals(1, entity.getLatestVersion());

            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), 1);
            assertNull(snapshot.getDelegatedSignerConnectorUuid());
            assertNotNull(snapshot.getSigningScheme());
            assertNotNull(snapshot.getWorkflowType());
        }

        @Test
        void staticKeyManaged_assertSchemeAndEntityFields()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileRequestDto request = buildManagedStaticKeyRawRequest("static-key-profile");

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());
            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), 1);
            assertEquals(ManagedSigningType.STATIC_KEY, snapshot.getManagedSigningType());
            assertNull(snapshot.getDelegatedSignerConnectorUuid());
            assertNull(snapshot.getRaProfileUuid());
            assertNull(snapshot.getCsrTemplateUuid());
        }

        @Test
        void oneTimeKeyManaged_assertSchemeAndEntityFields()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileRequestDto request = buildManagedOneTimeKeyRawRequest("one-time-key-profile");

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());
            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), 1);
            assertEquals(ManagedSigningType.ONE_TIME_KEY, snapshot.getManagedSigningType());
            assertNull(snapshot.getDelegatedSignerConnectorUuid());
            assertNull(snapshot.getCertificateUuid());
        }

        @Test
        void staticKeyManaged_incompleteChain_throwsValidationException()
                throws CertificateException, NoSuchAlgorithmException, OperatorCreationException {
            // given: a certificate whose chain cannot be verified
            Certificate incompleteChainCert = buildIncompleteChainCertificate();
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(incompleteChainCert.getUuid());
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("incomplete-chain-profile");
            request.setSigningScheme(scheme);
            request.setWorkflow(new RawSigningWorkflowRequestDto());

            // when/then
            assertThrows(ValidationException.class,
                    () -> signingProfileService.createSigningProfile(request),
                    "createSigningProfile must reject a certificate whose chain is incomplete");
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CreateWorkflow {

        Stream<Arguments> basicWorkflowTypes() {
            return Stream.of(
                    Arguments.of(SigningWorkflowType.CONTENT_SIGNING),
                    Arguments.of(SigningWorkflowType.TIMESTAMPING)
            );
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @MethodSource("basicWorkflowTypes")
        void workflowType_persistedInDtoAndEntity(SigningWorkflowType expectedType)
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: build the request after setUp has initialised formatterConnector
            SigningProfileRequestDto request = expectedType == SigningWorkflowType.CONTENT_SIGNING
                    ? buildDelegatedContentRequest("content-signing-profile")
                    : buildDelegatedTimestampingRequest("timestamping-profile");

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertNotNull(dto.getWorkflow());
            assertEquals(expectedType, dto.getWorkflow().getType());
            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(expectedType, entity.getWorkflowType());
        }

        @Test
        void timestampingWorkflowWithPoliciesAndAlgorithms_assertEntityFields()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
            timestampingWorkflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            timestampingWorkflow.setDefaultPolicyId("1.2.3.4.5");
            timestampingWorkflow.setAllowedPolicyIds(List.of("1.2.3.4.5", "1.2.3.4.6"));
            timestampingWorkflow.setAllowedDigestAlgorithms(List.of(DigestAlgorithm.SHA_256, DigestAlgorithm.SHA_384));
            timestampingWorkflow.setQualifiedTimestamp(false);
            timestampingWorkflow.setValidateTokenSignature(true);

            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("timestamping-with-policies");
            request.setDescription("Timestamping profile with policies");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(timestampingWorkflow);

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then: DTO
            assertEquals(SigningWorkflowType.TIMESTAMPING, dto.getWorkflow().getType());
            TimestampingWorkflowDto tsDto = (TimestampingWorkflowDto) dto.getWorkflow();
            assertEquals("1.2.3.4.5", tsDto.getDefaultPolicyId());
            assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), tsDto.getAllowedPolicyIds());
            assertTrue(tsDto.getAllowedDigestAlgorithms().contains(DigestAlgorithm.SHA_256));
            assertTrue(tsDto.getAllowedDigestAlgorithms().contains(DigestAlgorithm.SHA_384));
            assertFalse(tsDto.getQualifiedTimestamp());
            assertTrue(tsDto.getValidateTokenSignature());

            // then: DB
            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(SigningWorkflowType.TIMESTAMPING, entity.getWorkflowType());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), 1);
            assertEquals("1.2.3.4.5", snapshot.getDefaultPolicyId());
            assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), snapshot.getAllowedPolicyIds());
            assertEquals(List.of(DigestAlgorithm.SHA_256.getCode(), DigestAlgorithm.SHA_384.getCode()),
                    snapshot.getAllowedDigestAlgorithms());
            assertFalse(snapshot.getQualifiedTimestamp());
            assertTrue(snapshot.getValidateTokenSignature());
        }

        @Test
        void managedStaticKey_withContentSigningWorkflow_assertBothFields()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            StaticKeyManagedSigningRequestDto managedContentScheme = new StaticKeyManagedSigningRequestDto();
            managedContentScheme.setCertificateUuid(certificate.getUuid());
            ContentSigningWorkflowRequestDto managedContentWorkflow = new ContentSigningWorkflowRequestDto();
            managedContentWorkflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());

            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("managed-content-profile");
            request.setDescription("Managed static-key profile with content signing workflow");
            request.setSigningScheme(managedContentScheme);
            request.setWorkflow(managedContentWorkflow);

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertEquals(SigningScheme.MANAGED, dto.getSigningScheme().getSigningScheme());
            assertEquals(SigningWorkflowType.CONTENT_SIGNING, dto.getWorkflow().getType());
            assertFalse(dto.isEnabled());

            SigningProfile entity = reloadProfile(UUID.fromString(dto.getUuid()));
            assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
            assertEquals(SigningWorkflowType.CONTENT_SIGNING, entity.getWorkflowType());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), 1);
            assertEquals(ManagedSigningType.STATIC_KEY, snapshot.getManagedSigningType());
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void assertDtoAndDbEntity()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileRequestDto request = buildDelegatedRawRequest("updated-profile");
            request.setDescription("Updated description");

            // when
            SigningProfileDto dto = signingProfileService.updateSigningProfile(
                    savedProfile.getSecuredUuid(), request);

            // then: DTO
            assertNotNull(dto);
            assertEquals(savedProfile.getUuid().toString(), dto.getUuid());
            assertEquals("updated-profile", dto.getName());
            assertEquals("Updated description", dto.getDescription());
            assertFalse(dto.isEnabled());
            assertEquals(1, dto.getVersion()); // no bump — no signing records

            // then: DB
            SigningProfile entity = reloadProfile(savedProfile.getUuid());
            assertEquals("updated-profile", entity.getName());
            assertEquals("Updated description", entity.getDescription());
            assertFalse(entity.getEnabled());
            assertEquals(1, entity.getLatestVersion());
            assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
            assertEquals(SigningWorkflowType.RAW_SIGNING, entity.getWorkflowType());
        }

        @Test
        void withSigningRecordsOnCurrentVersion_bumpsVersion()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a signing record linked to version 1
            createSigningRecordFor(savedProfile, 1);
            SigningProfileRequestDto request = buildDelegatedRawRequest("profile-with-bump");

            // when
            SigningProfileDto dto = signingProfileService.updateSigningProfile(
                    savedProfile.getSecuredUuid(), request);

            // then
            assertEquals(2, dto.getVersion());
            assertEquals(2, reloadProfile(savedProfile.getUuid()).getLatestVersion());
            assertTrue(
                    signingProfileVersionRepository.findBySigningProfileUuidAndVersion(savedProfile.getUuid(), 2).isPresent(),
                    "Version 2 snapshot should be created after bump");
        }

        @Test
        void versionBump_oldVersionAttributesPreservedInEngine()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: STATIC_KEY profile (v1) with PKCS1-v1_5/SHA-256 signing-op attributes
            StaticKeyManagedSigningRequestDto schemeV1 = new StaticKeyManagedSigningRequestDto();
            schemeV1.setCertificateUuid(rsaCertificate.getUuid());
            schemeV1.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("versioned-sign-attrs-preserved");
            createRequest.setSigningScheme(schemeV1);
            createRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            UUID profileUuid = UUID.fromString(created.getUuid());

            List<ResponseAttribute> v1Attrs = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .operation(AttributeOperation.SIGN).version(1).build());
            assertFalse(v1Attrs.isEmpty(), "Version 1 signing-op attributes should be stored");

            // given: version bump — add signing record for v1, update with PSS/SHA-512
            createSigningRecordFor(reloadProfile(profileUuid), 1);
            StaticKeyManagedSigningRequestDto schemeV2 = new StaticKeyManagedSigningRequestDto();
            schemeV2.setCertificateUuid(rsaCertificate.getUuid());
            schemeV2.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                    buildDigestAttribute(DigestAlgorithm.SHA_512)));
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("versioned-sign-attrs-preserved");
            updateRequest.setSigningScheme(schemeV2);
            updateRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto updated = signingProfileService.updateSigningProfile(
                    SecuredUUID.fromUUID(profileUuid), updateRequest);

            // then
            assertEquals(2, updated.getVersion());
            List<ResponseAttribute> v1AttrAfterBump = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .operation(AttributeOperation.SIGN).version(1).build());
            assertFalse(v1AttrAfterBump.isEmpty(),
                    "Version 1 signing-op attributes must be preserved after a version bump");
            List<ResponseAttribute> v2Attrs = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .operation(AttributeOperation.SIGN).version(2).build());
            assertFalse(v2Attrs.isEmpty(), "Version 2 signing-op attributes should be stored after bump");
        }

        @Test
        void versionBump_oldFormatterAttributesPreservedInEngine()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: CONTENT_SIGNING profile (v1) with formatter attributes
            Connector formatter = createFormatterConnector("formatter-bump-preserve");
            FormatterAttr attr = registerFormatterAttribute(formatter, "Bump Preserve Attribute");

            ContentSigningWorkflowRequestDto wfV1 = new ContentSigningWorkflowRequestDto();
            wfV1.setSignatureFormatterConnectorUuid(formatter.getUuid());
            wfV1.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "v1-value")));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("formatter-attrs-bump-preserve");
            createRequest.setSigningScheme(new DelegatedSigningRequestDto());
            createRequest.setWorkflow(wfV1);
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            UUID profileUuid = UUID.fromString(created.getUuid());

            List<ResponseAttribute> v1AttrsBefore = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .connector(formatter.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(1).build());
            assertFalse(v1AttrsBefore.isEmpty(), "Version 1 formatter attributes should be stored after create");

            // given: version bump
            createSigningRecordFor(reloadProfile(profileUuid), 1);
            ContentSigningWorkflowRequestDto wfV2 = new ContentSigningWorkflowRequestDto();
            wfV2.setSignatureFormatterConnectorUuid(formatter.getUuid());
            wfV2.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "v2-value")));
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("formatter-attrs-bump-preserve");
            updateRequest.setSigningScheme(new DelegatedSigningRequestDto());
            updateRequest.setWorkflow(wfV2);
            SigningProfileDto updated = signingProfileService.updateSigningProfile(
                    SecuredUUID.fromUUID(profileUuid), updateRequest);

            // then
            assertEquals(2, updated.getVersion(), "Version must be bumped to 2");
            List<ResponseAttribute> v1AttrsAfterBump = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .connector(formatter.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(1).build());
            assertFalse(v1AttrsAfterBump.isEmpty(),
                    "Version 1 formatter attributes must be preserved after a version bump");
            List<ResponseAttribute> v2Attrs = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .connector(formatter.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(2).build());
            assertFalse(v2Attrs.isEmpty(), "Version 2 formatter attributes should be stored after bump");
        }

        @Test
        void changeSchemeFromDelegatedToStaticKeyManaged()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: savedProfile uses DELEGATED scheme
            assertEquals(SigningScheme.DELEGATED, savedProfile.getSigningScheme());

            // when: update to MANAGED/STATIC_KEY
            signingProfileService.updateSigningProfile(
                    savedProfile.getSecuredUuid(), buildManagedStaticKeyRawRequest("scheme-switched"));

            // then
            SigningProfile entity = reloadProfile(savedProfile.getUuid());
            assertEquals(SigningScheme.MANAGED, entity.getSigningScheme());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), entity.getLatestVersion());
            assertEquals(ManagedSigningType.STATIC_KEY, snapshot.getManagedSigningType());
            assertNull(snapshot.getDelegatedSignerConnectorUuid());
        }

        @Test
        void staticKeyManaged_incompleteChain_throwsValidationException()
                throws CertificateException, NoSuchAlgorithmException, OperatorCreationException {
            // given
            Certificate incompleteChainCert = buildIncompleteChainCertificate();
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(incompleteChainCert.getUuid());
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("incomplete-chain-update");
            request.setSigningScheme(scheme);
            request.setWorkflow(new RawSigningWorkflowRequestDto());

            // when/then
            assertThrows(ValidationException.class,
                    () -> signingProfileService.updateSigningProfile(savedProfile.getSecuredUuid(), request),
                    "updateSigningProfile must reject a certificate whose chain is incomplete");
        }

        @Test
        void changeSchemeFromStaticKeyManagedToDelegated()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: create a MANAGED/STATIC_KEY profile
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildManagedStaticKeyRawRequest("managed-to-delegated"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            // when: switch to DELEGATED
            signingProfileService.updateSigningProfile(profileUuid, buildDelegatedRawRequest("managed-to-delegated"));

            // then
            SigningProfile entity = reloadProfile(UUID.fromString(created.getUuid()));
            assertEquals(SigningScheme.DELEGATED, entity.getSigningScheme());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), entity.getLatestVersion());
            assertNull(snapshot.getManagedSigningType());
            assertNull(snapshot.getCertificateUuid());
        }

        @Test
        void changeWorkflowFromRawToTimestamping()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: savedProfile uses RAW_SIGNING
            TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
            timestampingWorkflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            timestampingWorkflow.setDefaultPolicyId("1.2.3.4.5");
            timestampingWorkflow.setAllowedPolicyIds(List.of("1.2.3.4.5"));
            timestampingWorkflow.setQualifiedTimestamp(false);
            timestampingWorkflow.setValidateTokenSignature(false);

            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("workflow-changed");
            request.setDescription("Changed to timestamping");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(timestampingWorkflow);

            // when
            SigningProfileDto dto = signingProfileService.updateSigningProfile(
                    savedProfile.getSecuredUuid(), request);

            // then
            assertEquals(SigningWorkflowType.TIMESTAMPING, dto.getWorkflow().getType());
            SigningProfile entity = reloadProfile(savedProfile.getUuid());
            assertEquals(SigningWorkflowType.TIMESTAMPING, entity.getWorkflowType());
            SigningProfileVersion snapshot = loadVersionSnapshot(entity.getUuid(), entity.getLatestVersion());
            assertEquals("1.2.3.4.5", snapshot.getDefaultPolicyId());
            assertFalse(snapshot.getQualifiedTimestamp());
            assertFalse(snapshot.getValidateTokenSignature());
        }

        @Test
        void noVersionBump_overwritesExistingSnapshot()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: profile created via service (proper v1 snapshot), no signing records
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("overwrite-snapshot-profile"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
            UUID profileUuidRaw = UUID.fromString(created.getUuid());

            // when: update without signing records
            signingProfileService.updateSigningProfile(profileUuid,
                    buildDelegatedContentRequest("overwrite-snapshot-profile"));

            // then: still only v1 snapshot (overwritten)
            assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1).isPresent());
            assertFalse(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent(),
                    "No version 2 snapshot should exist when version was not bumped");
            SigningProfileDto v1 = signingProfileService.getSigningProfile(profileUuid, 1);
            assertEquals(SigningWorkflowType.CONTENT_SIGNING, v1.getWorkflow().getType(),
                    "Overwritten v1 snapshot should reflect the new workflow type");
        }

        @Test
        void multipleBumps_versionsAccumulate()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: profile at v1 (RAW_SIGNING)
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("multi-bump-profile"));
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
            UUID profileUuidRaw = UUID.fromString(created.getUuid());

            // when: bump to v2 (CONTENT_SIGNING)
            createSigningRecordFor(reloadProfile(profileUuidRaw), 1);
            signingProfileService.updateSigningProfile(profileUuid, buildDelegatedContentRequest("multi-bump-profile"));

            // when: bump to v3 (TIMESTAMPING)
            createSigningRecordFor(reloadProfile(profileUuidRaw), 2);
            signingProfileService.updateSigningProfile(profileUuid, buildDelegatedTimestampingRequest("multi-bump-profile"));

            // then: 3 snapshots
            assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 1).isPresent());
            assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 2).isPresent());
            assertTrue(signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuidRaw, 3).isPresent());

            assertEquals(3, signingProfileService.getSigningProfile(profileUuid, null).getVersion());
            assertEquals(SigningWorkflowType.TIMESTAMPING,
                    signingProfileService.getSigningProfile(profileUuid, null).getWorkflow().getType());
            assertEquals(SigningWorkflowType.RAW_SIGNING,
                    signingProfileService.getSigningProfile(profileUuid, 1).getWorkflow().getType());
            assertEquals(SigningWorkflowType.CONTENT_SIGNING,
                    signingProfileService.getSigningProfile(profileUuid, 2).getWorkflow().getType());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DeleteTests {

        @Test
        void removesEntityFromDatabase() throws NotFoundException {
            // given: savedProfile has no dependents
            // when
            signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid());

            // then
            assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
            assertThrows(NotFoundException.class,
                    () -> signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null));
        }

        Stream<Arguments> blockedDeleteScenarios() {
            return Stream.of(
                    Arguments.of(
                            (Executable) () -> createSigningRecordFor(savedProfile, 1),
                            "blocked by signing records",
                            "signing records"
                    ),
                    Arguments.of(
                            (Executable) () -> {
                                TspProfile tsp = new TspProfile();
                                tsp.setName("expected-tsp-name");
                                tsp.setDefaultSigningProfile(savedProfile);
                                tspRepository.save(tsp);
                            },
                            "blocked by TSP profile as default",
                            "expected-tsp-name"
                    )
            );
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("blockedDeleteScenarios")
        void blockedBy_throwsValidationExceptionWithMessage(
                Executable setup, String displayName, String expectedMessageFragment)
                throws Throwable {
            // given: set up the blocking dependency
            setup.execute();

            // when
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> signingProfileService.deleteSigningProfile(savedProfile.getSecuredUuid()));

            // then: profile still exists; error message mentions the blocker
            assertTrue(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
            String message = ex.getErrors().stream()
                    .map(ValidationError::getErrorDescription)
                    .findFirst().orElse("");
            assertTrue(message.contains(expectedMessageFragment),
                    "Error message should contain '" + expectedMessageFragment + "', got: " + message);
        }
    }

    @Nested
    class BulkDelete {

        @Test
        void removesAllEntities()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a second profile
            SigningProfileDto second = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("second-profile"));

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                    List.of(savedProfile.getSecuredUuid(), SecuredUUID.fromString(second.getUuid())));

            // then
            assertTrue(messages.isEmpty(), "Expected no errors but got: " + messages);
            assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
            assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).isPresent());
        }

        @Test
        void withSigningRecords_returnsErrorAndLeavesBlockedProfileIntact()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: savedProfile has signing record; second has none
            createSigningRecordFor(savedProfile, 1);
            SigningProfileDto second = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("second-profile-no-deps"));

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                    List.of(savedProfile.getSecuredUuid(), SecuredUUID.fromString(second.getUuid())));

            // then: savedProfile blocked, second deleted
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().anyMatch(m -> savedProfile.getUuid().toString().equals(m.getUuid())));
            assertTrue(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
            assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).isPresent());
        }

        @Test
        void withTspProfileDependency_returnsErrorAndLeavesBlockedProfileIntact()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: savedProfile is default in a TSP profile; second has none
            TspProfile tspProfile = new TspProfile();
            tspProfile.setName("blocking-tsp");
            tspProfile.setDefaultSigningProfile(savedProfile);
            tspRepository.save(tspProfile);
            SigningProfileDto second = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("unblocked-profile"));

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                    List.of(savedProfile.getSecuredUuid(), SecuredUUID.fromString(second.getUuid())));

            // then
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().anyMatch(m -> savedProfile.getUuid().toString().equals(m.getUuid())));
            assertTrue(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
            assertFalse(signingProfileRepository.findById(UUID.fromString(second.getUuid())).isPresent());
        }

        @Test
        void emptyList_returnsEmptyMessages() {
            // given: an empty list of UUIDs
            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(List.of());

            // then
            assertNotNull(messages);
            assertTrue(messages.isEmpty());
        }

        @Test
        void withNonExistentUuid_silentlyIgnoresUnknown() {
            // given: one unknown UUID + one known
            SecuredUUID unknownUuid = SecuredUUID.fromString("00000000-0000-0000-0000-000000000099");

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDeleteSigningProfiles(
                    List.of(unknownUuid, savedProfile.getSecuredUuid()));

            // then: error returned for unknown, known profile deleted
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().anyMatch(m -> "00000000-0000-0000-0000-000000000099".equals(m.getUuid())));
            assertFalse(signingProfileRepository.findById(savedProfile.getUuid()).isPresent());
        }
    }

    @Nested
    class EnableDisable {

        @Test
        void enableSigningProfile() throws NotFoundException {
            // given: profile is disabled (from setUp)
            assertFalse(savedProfile.getEnabled());

            // when
            signingProfileService.enableSigningProfile(savedProfile.getSecuredUuid());

            // then
            assertTrue(signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null).isEnabled());
            assertTrue(reloadProfile(savedProfile.getUuid()).getEnabled());
        }

        @Test
        void enableSigningProfile_alreadyEnabled_remainsEnabled() throws NotFoundException {
            // given
            savedProfile.setEnabled(true);
            signingProfileRepository.save(savedProfile);

            // when: enable again — should be idempotent
            signingProfileService.enableSigningProfile(savedProfile.getSecuredUuid());

            // then
            assertTrue(reloadProfile(savedProfile.getUuid()).getEnabled());
        }

        @Test
        void enableSigningProfile_afterCreate_persistsEnabledState()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileDto dto = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("enabled-profile"));
            assertFalse(dto.isEnabled(), "Profiles must be created in a disabled state");

            // when
            signingProfileService.enableSigningProfile(SecuredUUID.fromString(dto.getUuid()));

            // then
            assertTrue(reloadProfile(UUID.fromString(dto.getUuid())).getEnabled());
        }

        @Test
        void disableSigningProfile() throws NotFoundException {
            // given: profile is enabled
            savedProfile.setEnabled(true);
            signingProfileRepository.save(savedProfile);

            // when
            signingProfileService.disableSigningProfile(savedProfile.getSecuredUuid());

            // then
            assertFalse(signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null).isEnabled());
            assertFalse(reloadProfile(savedProfile.getUuid()).getEnabled());
        }

        @Test
        void disableSigningProfile_alreadyDisabled_remainsDisabled() throws NotFoundException {
            // given: savedProfile is already disabled from setUp
            // when
            signingProfileService.disableSigningProfile(savedProfile.getSecuredUuid());

            // then
            assertFalse(reloadProfile(savedProfile.getUuid()).getEnabled());
        }

        @Test
        void bulkEnableSigningProfiles_multipleProfiles()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileDto second = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("second-for-bulk-enable"));
            SigningProfileDto third = signingProfileService.createSigningProfile(
                    buildDelegatedContentRequest("third-for-bulk-enable"));

            // when
            signingProfileService.bulkEnableSigningProfiles(List.of(
                    savedProfile.getSecuredUuid(),
                    SecuredUUID.fromString(second.getUuid()),
                    SecuredUUID.fromString(third.getUuid())));

            // then
            assertTrue(reloadProfile(savedProfile.getUuid()).getEnabled());
            assertTrue(reloadProfile(UUID.fromString(second.getUuid())).getEnabled());
            assertTrue(reloadProfile(UUID.fromString(third.getUuid())).getEnabled());
        }

        @Test
        void bulkDisableSigningProfiles_multipleProfiles()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: two additional enabled profiles
            SigningProfileDto second = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("second-for-bulk-disable"));
            signingProfileService.enableSigningProfile(SecuredUUID.fromString(second.getUuid()));
            SigningProfileDto third = signingProfileService.createSigningProfile(
                    buildDelegatedContentRequest("third-for-bulk-disable"));
            signingProfileService.enableSigningProfile(SecuredUUID.fromString(third.getUuid()));

            // when
            signingProfileService.bulkDisableSigningProfiles(List.of(
                    SecuredUUID.fromString(second.getUuid()),
                    SecuredUUID.fromString(third.getUuid())));

            // then
            assertFalse(reloadProfile(UUID.fromString(second.getUuid())).getEnabled());
            assertFalse(reloadProfile(UUID.fromString(third.getUuid())).getEnabled());
        }

        @Test
        void bulkEnableSigningProfiles_withNonExistentUuid_silentlyIgnores() {
            // given
            SecuredUUID unknownUuid = SecuredUUID.fromString("00000000-0000-0000-0000-000000000099");

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkEnableSigningProfiles(
                    List.of(unknownUuid, savedProfile.getSecuredUuid()));

            // then: error for unknown UUID, known profile enabled
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().anyMatch(m -> "00000000-0000-0000-0000-000000000099".equals(m.getUuid())));
            assertTrue(reloadProfile(savedProfile.getUuid()).getEnabled());
        }

        @Test
        void bulkDisableSigningProfiles_withNonExistentUuid_silentlyIgnores() {
            // given
            savedProfile.setEnabled(true);
            signingProfileRepository.save(savedProfile);
            SecuredUUID unknownUuid = SecuredUUID.fromString("00000000-0000-0000-0000-000000000099");

            // when
            List<BulkActionMessageDto> messages = signingProfileService.bulkDisableSigningProfiles(
                    List.of(unknownUuid, savedProfile.getSecuredUuid()));

            // then: error for unknown UUID, known profile disabled
            assertFalse(messages.isEmpty());
            assertTrue(messages.stream().anyMatch(m -> "00000000-0000-0000-0000-000000000099".equals(m.getUuid())));
            assertFalse(reloadProfile(savedProfile.getUuid()).getEnabled());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TspProtocol {

        @Test
        void activateTsp_setsLinkOnSigningProfile()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileDto profileDto = signingProfileService.createSigningProfile(
                    buildDelegatedTimestampingRequest("timestamping-for-tsp-activate"));
            SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());
            TspProfile tspProfile = new TspProfile();
            tspProfile.setName("test-tsp-profile");
            tspProfile = tspRepository.save(tspProfile);

            // when
            var activationDto = signingProfileService.activateTsp(profileUuid, tspProfile.getSecuredUuid());

            // then
            assertTrue(activationDto.isAvailable());
            assertNotNull(activationDto.getSigningUrl());
            assertEquals(tspProfile.getUuid(),
                    reloadProfile(UUID.fromString(profileDto.getUuid())).getTspProfileUuid());
        }

        @Test
        void activateTsp_tspProfileNotFound_throwsNotFoundException()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a valid signing profile but a missing TSP UUID
            SigningProfileDto profileDto = signingProfileService.createSigningProfile(
                    buildDelegatedTimestampingRequest("timestamping-for-tsp-not-found"));
            SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());

            // when/then
            assertThrows(NotFoundException.class,
                    () -> signingProfileService.activateTsp(
                            profileUuid, SecuredUUID.fromString("00000000-0000-0000-0000-000000000002")));
        }

        @Test
        void activateTsp_replacesExistingLink()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileDto profileDto = signingProfileService.createSigningProfile(
                    buildDelegatedTimestampingRequest("timestamping-for-tsp-replace"));
            SecuredUUID profileUuid = SecuredUUID.fromString(profileDto.getUuid());
            TspProfile tspProfile1 = new TspProfile();
            tspProfile1.setName("tsp-profile-1");
            tspProfile1 = tspRepository.save(tspProfile1);
            TspProfile tspProfile2 = new TspProfile();
            tspProfile2.setName("tsp-profile-2");
            tspProfile2 = tspRepository.save(tspProfile2);

            // when: link first, then replace with second
            signingProfileService.activateTsp(profileUuid, tspProfile1.getSecuredUuid());
            signingProfileService.activateTsp(profileUuid, tspProfile2.getSecuredUuid());

            // then
            assertEquals(tspProfile2.getUuid(),
                    reloadProfile(UUID.fromString(profileDto.getUuid())).getTspProfileUuid());
        }

        // @MethodSource runs during test discovery (before @BeforeEach), so formatterConnector
        // is not yet initialized. Pass only a workflow type and build the profile lazily inside the test.
        Stream<Arguments> unsupportedWorkflowTypes() {
            return Stream.of(
                    Arguments.of(SigningWorkflowType.RAW_SIGNING, "RAW_SIGNING"),
                    Arguments.of(SigningWorkflowType.CONTENT_SIGNING, "CONTENT_SIGNING")
            );
        }

        @ParameterizedTest(name = "[{index}] {1}")
        @MethodSource("unsupportedWorkflowTypes")
        void activateTsp_unsupportedWorkflow_throwsValidationException(
                SigningWorkflowType workflowType, String displayName)
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a signing profile with an unsupported workflow type
            SecuredUUID profileUuid;
            if (workflowType == SigningWorkflowType.RAW_SIGNING) {
                profileUuid = savedProfile.getSecuredUuid();
            } else {
                profileUuid = SecuredUUID.fromString(
                        signingProfileService.createSigningProfile(
                                buildDelegatedContentRequest("content-for-tsp-unsupported-" + displayName)).getUuid());
            }
            TspProfile tspProfile = new TspProfile();
            tspProfile.setName("tsp-unsupported-" + displayName.hashCode());
            tspProfile = tspRepository.save(tspProfile);
            final SecuredUUID tspUuid = tspProfile.getSecuredUuid();

            // when/then
            assertThrows(ValidationException.class,
                    () -> signingProfileService.activateTsp(profileUuid, tspUuid));
        }

        @Test
        void deactivateTsp_removesFromEnabledProtocols() throws NotFoundException {
            // given: savedProfile linked to a TSP
            TspProfile tspProfile = new TspProfile();
            tspProfile.setName("tsp-to-deactivate");
            tspProfile = tspRepository.save(tspProfile);
            savedProfile.setTspProfile(tspProfile);
            signingProfileRepository.save(savedProfile);
            assertTrue(signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null)
                    .getEnabledProtocols().contains(SigningProtocol.TSP));

            // when
            signingProfileService.deactivateTsp(savedProfile.getSecuredUuid());

            // then
            assertNull(reloadProfile(savedProfile.getUuid()).getTspProfileUuid());
            assertFalse(signingProfileService.getSigningProfile(savedProfile.getSecuredUuid(), null)
                    .getEnabledProtocols().contains(SigningProtocol.TSP));
        }

        @Test
        void deactivateTsp_noLinkExists_isIdempotent() {
            // given: savedProfile has no TSP link (setUp leaves it null)
            assertNull(savedProfile.getTspProfileUuid());

            // when/then: no exception
            assertDoesNotThrow(() -> signingProfileService.deactivateTsp(savedProfile.getSecuredUuid()));
            assertNull(reloadProfile(savedProfile.getUuid()).getTspProfileUuid());
        }
    }

    @Nested
    class SigningOperationAttributes {

        @Test
        void specificVersion_returnsVersionedSigningOperationAttributes()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: v1 with PSS, v2 with PKCS1-v1_5
            StaticKeyManagedSigningRequestDto schemeV1 = new StaticKeyManagedSigningRequestDto();
            schemeV1.setCertificateUuid(rsaCertificate.getUuid());
            schemeV1.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("versioned-get-sign-attrs");
            createRequest.setSigningScheme(schemeV1);
            createRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            createSigningRecordFor(reloadProfile(UUID.fromString(created.getUuid())), 1);
            StaticKeyManagedSigningRequestDto schemeV2 = new StaticKeyManagedSigningRequestDto();
            schemeV2.setCertificateUuid(rsaCertificate.getUuid());
            schemeV2.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("versioned-get-sign-attrs");
            updateRequest.setSigningScheme(schemeV2);
            updateRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            signingProfileService.updateSigningProfile(profileUuid, updateRequest);

            // when: get v1 — must show PSS; get v2 — must show PKCS1-v1_5 (attribute names are version-agnostic)
            SigningProfileDto v1Dto = signingProfileService.getSigningProfile(profileUuid, 1);
            assertInstanceOf(StaticKeyManagedSigningDto.class, v1Dto.getSigningScheme());
            StaticKeyManagedSigningDto v1Scheme = (StaticKeyManagedSigningDto) v1Dto.getSigningScheme();
            assertFalse(v1Scheme.getSigningOperationAttributes().isEmpty());
            assertTrue(v1Scheme.getSigningOperationAttributes().stream()
                    .anyMatch(a -> RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME.equals(a.getName())));

            SigningProfileDto v2Dto = signingProfileService.getSigningProfile(profileUuid, 2);
            assertInstanceOf(StaticKeyManagedSigningDto.class, v2Dto.getSigningScheme());
            assertFalse(((StaticKeyManagedSigningDto) v2Dto.getSigningScheme()).getSigningOperationAttributes().isEmpty());

            // then: engine holds distinct content for each version
            List<ResponseAttribute> v1Engine = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, UUID.fromString(created.getUuid()))
                            .operation(AttributeOperation.SIGN).version(1).build());
            List<ResponseAttribute> v2Engine = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, UUID.fromString(created.getUuid()))
                            .operation(AttributeOperation.SIGN).version(2).build());
            assertFalse(v1Engine.isEmpty());
            assertFalse(v2Engine.isEmpty());
        }

        @Test
        void create_persistedAndReturnedInDto()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(rsaCertificate.getUuid());
            scheme.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("static-key-with-sign-attrs");
            request.setSigningScheme(scheme);
            request.setWorkflow(new RawSigningWorkflowRequestDto());

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertInstanceOf(StaticKeyManagedSigningDto.class, dto.getSigningScheme());
            StaticKeyManagedSigningDto schemeDto = (StaticKeyManagedSigningDto) dto.getSigningScheme();
            assertFalse(schemeDto.getSigningOperationAttributes().isEmpty());
            assertTrue(schemeDto.getSigningOperationAttributes().stream()
                    .anyMatch(a -> RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME.equals(a.getName())));
        }

        @Test
        void get_loadedFromEngine()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: create then re-fetch
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(rsaCertificate.getUuid());
            scheme.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("static-key-get-sign-attrs");
            request.setSigningScheme(scheme);
            request.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(request);

            // when
            SigningProfileDto fetched = signingProfileService.getSigningProfile(
                    SecuredUUID.fromString(created.getUuid()), null);

            // then: attributes survive create→get round-trip
            assertInstanceOf(StaticKeyManagedSigningDto.class, fetched.getSigningScheme());
            assertFalse(((StaticKeyManagedSigningDto) fetched.getSigningScheme())
                    .getSigningOperationAttributes().isEmpty());
        }

        @Test
        void update_replacesAttributes()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: create with PKCS1-v1_5
            StaticKeyManagedSigningRequestDto schemeV1 = new StaticKeyManagedSigningRequestDto();
            schemeV1.setCertificateUuid(rsaCertificate.getUuid());
            schemeV1.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("static-key-update-sign-attrs");
            createRequest.setSigningScheme(schemeV1);
            createRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            // when: update to PSS/SHA-384
            StaticKeyManagedSigningRequestDto schemeV2 = new StaticKeyManagedSigningRequestDto();
            schemeV2.setCertificateUuid(rsaCertificate.getUuid());
            schemeV2.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                    buildDigestAttribute(DigestAlgorithm.SHA_384)));
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("static-key-update-sign-attrs");
            updateRequest.setSigningScheme(schemeV2);
            updateRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto updated = signingProfileService.updateSigningProfile(profileUuid, updateRequest);

            // then: new value (PSS) is reflected in the returned DTO
            assertInstanceOf(StaticKeyManagedSigningDto.class, updated.getSigningScheme());
            StaticKeyManagedSigningDto schemeDto = (StaticKeyManagedSigningDto) updated.getSigningScheme();
            Optional<ResponseAttribute> rsaAttr = schemeDto.getSigningOperationAttributes().stream()
                    .filter(a -> RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME.equals(a.getName()))
                    .findFirst();
            assertTrue(rsaAttr.isPresent());
            String actualCode = switch (rsaAttr.get().getVersion()) {
                case V2 -> ((ResponseAttributeV2) rsaAttr.get()).getContent().getFirst().getData().toString();
                case V3 -> ((ResponseAttributeV3) rsaAttr.get()).getContent().getFirst().getData().toString();
                default -> fail("Unknown attribute version: " + rsaAttr.get().getVersion());
            };
            assertEquals(RsaSignatureScheme.PSS.getCode(), actualCode,
                    "Signing operation attributes should be replaced with PSS on update");
        }

        @Test
        void update_schemeChangedToNonStaticKey_attributesCleared()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: STATIC_KEY profile with signing-op attributes
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(rsaCertificate.getUuid());
            scheme.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("static-key-to-delegated");
            createRequest.setSigningScheme(scheme);
            createRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());

            // when: switch to DELEGATED
            signingProfileService.updateSigningProfile(profileUuid, buildDelegatedRawRequest("static-key-to-delegated"));

            // then: no attributes remain in engine
            List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, UUID.fromString(created.getUuid()))
                            .operation(AttributeOperation.SIGN).version(1).build());
            assertTrue(remaining.isEmpty(),
                    "Signing-scheme attributes should be deleted when scheme changes away from STATIC_KEY");
        }

        @Test
        void delete_removesAttributesFromEngine()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(rsaCertificate.getUuid());
            scheme.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PSS),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("delete-clears-sign-attrs");
            request.setSigningScheme(scheme);
            request.setWorkflow(new RawSigningWorkflowRequestDto());
            SigningProfileDto created = signingProfileService.createSigningProfile(request);
            UUID profileUuid = UUID.fromString(created.getUuid());

            // when
            signingProfileService.deleteSigningProfile(SecuredUUID.fromUUID(profileUuid));

            // then
            List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .operation(AttributeOperation.SIGN).version(1).build());
            assertTrue(remaining.isEmpty());
        }
    }

    /**
     * Builds a valid RSA {@code signingOperationAttributes} request attribute for use in tests.
     */
    private RequestAttributeV2 buildRsaSchemeAttribute(RsaSignatureScheme scheme) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2(scheme.getLabel(), scheme.getCode());
        attr.setContent(List.of(content));
        return attr;
    }

    /**
     * Builds a valid digest {@code signingOperationAttributes} request attribute for use in tests.
     */
    private RequestAttributeV2 buildDigestAttribute(DigestAlgorithm algorithm) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2(algorithm.getLabel(), algorithm.getCode());
        attr.setContent(List.of(content));
        return attr;
    }

    /**
     * Creates and persists a minimal {@link Connector} entity for use as a signature formatter connector,
     * also pre-registering a simple data attribute definition so that AttributeEngine can accept content.
     */
    private Connector createFormatterConnector(String name) {
        Connector connector = new Connector();
        connector.setName(name);
        connector.setUrl("http://localhost:" + mockServer.port() + "/" + name);
        connector.setVersion(ConnectorVersion.V2);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        ConnectorInterfaceEntity connectorInterface = new ConnectorInterfaceEntity();
        connectorInterface.setConnectorUuid(connector.getUuid());
        connectorInterface.setInterfaceCode(ConnectorInterface.SIGNATURE_FORMATTING);
        connectorInterface.setVersion("1.0.0");
        connectorInterface.setFeatures(List.of(FeatureFlag.CONTENT_SIGNING, FeatureFlag.TIMESTAMPING));
        connectorInterfaceRepository.save(connectorInterface);

        return connector;
    }

    /**
     * Builds a {@link RequestAttributeV2} to use as a formatter connector attribute in tests.
     * The UUID and name here are arbitrary but must be pre-registered via
     * {@link AttributeEngine#updateDataAttributeDefinitions} before being stored.
     */
    private RequestAttributeV2 buildFormatterAttribute(UUID attrUuid, String attrName, String value) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(attrUuid);
        attr.setName(attrName);
        attr.setContentType(AttributeContentType.STRING);
        StringAttributeContentV2 content = new StringAttributeContentV2();
        content.setData(value);
        attr.setContent(List.of(content));
        return attr;
    }

    @Nested
    class FormatterAttributes {

        @Test
        void create_contentSigning_persistedAndReturnedInDto()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            Connector formatter = createFormatterConnector("formatter-content-create");
            FormatterAttr attr = registerFormatterAttribute(formatter, "Test Formatter Attribute");
            ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatter.getUuid());
            workflow.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "testValue")));
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("doc-profile-with-formatter-attrs");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(workflow);

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertInstanceOf(ContentSigningWorkflowDto.class, dto.getWorkflow());
            ContentSigningWorkflowDto wfDto = (ContentSigningWorkflowDto) dto.getWorkflow();
            assertFalse(wfDto.getSignatureFormatterConnectorAttributes().isEmpty());
            assertEquals(attr.name(), wfDto.getSignatureFormatterConnectorAttributes().getFirst().getName());
        }

        @Test
        void update_connectorChanged_oldAttributesCleared()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: create with formatterA
            Connector formatterA = createFormatterConnector("formatter-old");
            Connector formatterB = createFormatterConnector("formatter-new");
            FormatterAttr attr = registerFormatterAttribute(formatterA, "Switch Test Attribute");
            attributeEngine.updateDataAttributeDefinitions(formatterB.getUuid(),
                    AttributeOperation.WORKFLOW_FORMATTER,
                    List.of(buildDataAttrDef(attr.uuid(), attr.name())));

            ContentSigningWorkflowRequestDto workflowA = new ContentSigningWorkflowRequestDto();
            workflowA.setSignatureFormatterConnectorUuid(formatterA.getUuid());
            workflowA.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "valueA")));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("workflow-formatter-switch");
            createRequest.setSigningScheme(new DelegatedSigningRequestDto());
            createRequest.setWorkflow(workflowA);
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            SecuredUUID profileUuid = SecuredUUID.fromString(created.getUuid());
            UUID profileUuidRaw = UUID.fromString(created.getUuid());

            // when: update to formatterB
            ContentSigningWorkflowRequestDto workflowB = new ContentSigningWorkflowRequestDto();
            workflowB.setSignatureFormatterConnectorUuid(formatterB.getUuid());
            workflowB.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "valueB")));
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("workflow-formatter-switch");
            updateRequest.setSigningScheme(new DelegatedSigningRequestDto());
            updateRequest.setWorkflow(workflowB);
            signingProfileService.updateSigningProfile(profileUuid, updateRequest);

            // then: old formatterA attributes gone, new formatterB attributes present
            List<ResponseAttribute> oldAttrs = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuidRaw)
                            .connector(formatterA.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(1).build());
            assertTrue(oldAttrs.isEmpty());
            List<ResponseAttribute> newAttrs = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuidRaw)
                            .connector(formatterB.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(1).build());
            assertFalse(newAttrs.isEmpty());
        }

        @Test
        void get_returnedForContentAndTimestampingWorkflows()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: one formatter used by both CONTENT and TIMESTAMPING profiles
            Connector formatter = createFormatterConnector("formatter-multi-workflow");
            FormatterAttr attr = registerFormatterAttribute(formatter, "Multi Workflow Attribute");

            for (SigningWorkflowType workflowType : List.of(SigningWorkflowType.CONTENT_SIGNING, SigningWorkflowType.TIMESTAMPING)) {
                WorkflowRequestDto wfRequest = switch (workflowType) {
                    case CONTENT_SIGNING -> {
                        ContentSigningWorkflowRequestDto wf = new ContentSigningWorkflowRequestDto();
                        wf.setSignatureFormatterConnectorUuid(formatter.getUuid());
                        wf.setSignatureFormatterConnectorAttributes(
                                List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "val-" + workflowType)));
                        yield wf;
                    }
                    case TIMESTAMPING -> {
                        TimestampingWorkflowRequestDto wf = new TimestampingWorkflowRequestDto();
                        wf.setSignatureFormatterConnectorUuid(formatter.getUuid());
                        wf.setSignatureFormatterConnectorAttributes(
                                List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "val-" + workflowType)));
                        yield wf;
                    }
                    default -> throw new IllegalStateException("Unexpected: " + workflowType);
                };
                SigningProfileRequestDto request = new SigningProfileRequestDto();
                request.setName("formatter-attrs-" + workflowType);
                request.setSigningScheme(new DelegatedSigningRequestDto());
                request.setWorkflow(wfRequest);
                SigningProfileDto dto = signingProfileService.createSigningProfile(request);
                SigningProfileDto fetched = signingProfileService.getSigningProfile(
                        SecuredUUID.fromString(dto.getUuid()), null);

                List<ResponseAttribute> fetchedAttrs = switch (fetched.getWorkflow().getType()) {
                    case CONTENT_SIGNING ->
                            ((ContentSigningWorkflowDto) fetched.getWorkflow()).getSignatureFormatterConnectorAttributes();
                    case TIMESTAMPING ->
                            ((TimestampingWorkflowDto) fetched.getWorkflow()).getSignatureFormatterConnectorAttributes();
                    default -> throw new IllegalStateException("Unexpected: " + fetched.getWorkflow().getType());
                };
                assertFalse(fetchedAttrs.isEmpty(), "Formatter attributes should be loaded for: " + workflowType);
                assertEquals(attr.name(), fetchedAttrs.getFirst().getName());
            }
        }

        @Test
        void delete_removesAttributesFromEngine()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            Connector formatter = createFormatterConnector("formatter-delete-test");
            FormatterAttr attr = registerFormatterAttribute(formatter, "Delete Formatter Attribute");
            ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatter.getUuid());
            workflow.setSignatureFormatterConnectorAttributes(
                    List.of(buildFormatterAttribute(attr.uuid(), attr.name(), "toDelete")));
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("delete-clears-formatter-attrs");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(workflow);
            SigningProfileDto created = signingProfileService.createSigningProfile(request);
            UUID profileUuid = UUID.fromString(created.getUuid());

            // when
            signingProfileService.deleteSigningProfile(SecuredUUID.fromUUID(profileUuid));

            // then
            List<ResponseAttribute> remaining = attributeEngine.getObjectDataAttributesContent(
                    ObjectAttributeContentInfo.builder(Resource.SIGNING_PROFILE, profileUuid)
                            .connector(formatter.getUuid())
                            .operation(AttributeOperation.WORKFLOW_FORMATTER).version(1).build());
            assertTrue(remaining.isEmpty());
        }

        private DataAttributeV2 buildDataAttrDef(UUID uuid, String name) {
            DataAttributeV2 def = new DataAttributeV2();
            def.setUuid(uuid.toString());
            def.setName(name);
            def.setContentType(AttributeContentType.STRING);
            DataAttributeProperties props = new DataAttributeProperties();
            props.setLabel(name);
            def.setProperties(props);
            return def;
        }
    }

    @Nested
    class CustomAttributes {

        @Test
        void create_returnedInDto()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            RequestAttributeV3 customAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                    CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                    List.of(new StringAttributeContentV3("profile-value-on-create")));
            SigningProfileRequestDto request = buildDelegatedRawRequest("profile-with-custom-attr");
            request.setCustomAttributes(List.of(customAttr));

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then
            assertNotNull(dto.getCustomAttributes());
            assertFalse(dto.getCustomAttributes().isEmpty());
            assertEquals("profile-value-on-create",
                    ((ResponseAttributeV3) dto.getCustomAttributes().getFirst()).getContent().getFirst().getData());
        }

        @Test
        void update_returnedInDto()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: create then update
            RequestAttributeV3 createAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                    CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                    List.of(new StringAttributeContentV3("initial-value")));
            SigningProfileRequestDto createRequest = buildDelegatedRawRequest("profile-update-custom-attr");
            createRequest.setCustomAttributes(List.of(createAttr));
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);

            RequestAttributeV3 updateAttr = new RequestAttributeV3(UUID.fromString(CUSTOM_ATTR_UUID),
                    CUSTOM_ATTR_NAME, AttributeContentType.STRING,
                    List.of(new StringAttributeContentV3("updated-value")));
            SigningProfileRequestDto updateRequest = buildDelegatedRawRequest("profile-update-custom-attr");
            updateRequest.setCustomAttributes(List.of(updateAttr));

            // when
            SigningProfileDto updated = signingProfileService.updateSigningProfile(
                    SecuredUUID.fromString(created.getUuid()), updateRequest);

            // then
            assertNotNull(updated.getCustomAttributes());
            assertFalse(updated.getCustomAttributes().isEmpty());
            assertEquals("updated-value",
                    ((ResponseAttributeV3) updated.getCustomAttributes().getFirst()).getContent().getFirst().getData());
        }
    }

    @Nested
    class ManagedTimestampingModel {

        @Test
        void nonTimestampingProfile_throwsNotFoundException()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: a non-timestamping profile
            signingProfileService.createSigningProfile(buildManagedStaticKeyRawRequest("raw-profile-for-ts-check"));

            // when
            NotFoundException ex = assertThrows(NotFoundException.class,
                    () -> signingProfileService.getManagedTimestampingProfileModel("raw-profile-for-ts-check"));

            // then
            assertTrue(ex.getMessage().contains("not configured with a timestamping workflow"));
        }

        @Test
        void timestamping_staticKeyScheme_returnsTypedModelWithResolvedCertificate()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            signingProfileService.createSigningProfile(buildManagedStaticKeyTimestampingRequest("ts-managed-model"));

            // when
            SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ?> model =
                    signingProfileService.getManagedTimestampingProfileModel("ts-managed-model");

            // then
            assertInstanceOf(ManagedTimestampingWorkflow.class, model.workflow());
            assertInstanceOf(StaticKeyManagedSigning.class, model.signingScheme());
            StaticKeyManagedSigning schemeModel = (StaticKeyManagedSigning) model.signingScheme();
            assertNotNull(schemeModel.certificate());
            assertEquals(tsaCertificate.getUuid(), schemeModel.certificate().getUuid());
        }

        @Test
        void validationPropertiesRoundTrip()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            signingProfileService.createSigningProfile(
                    buildManagedStaticKeyTimestampingRequestWithValidationProps("ts-managed-validation-props"));

            // when
            SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ?> model =
                    signingProfileService.getManagedTimestampingProfileModel("ts-managed-validation-props");

            // then
            ManagedTimestampingWorkflow<?> wf = model.workflow();
            assertEquals("1.2.3.4.5", wf.defaultPolicyId());
            assertEquals(List.of("1.2.3.4.5", "1.2.3.4.6"), wf.allowedPolicyIds());
            assertEquals(List.of(DigestAlgorithm.SHA_256), wf.allowedDigestAlgorithms());
            assertTrue(wf.validateTokenSignature());
        }

        @Test
        void baseFieldsArePropagatedToModel()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileRequestDto request = buildManagedStaticKeyTimestampingRequest("ts-managed-base-fields");
            request.setDescription("expected ts description");
            SigningProfileDto created = signingProfileService.createSigningProfile(request);

            // when
            SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ?> model =
                    signingProfileService.getManagedTimestampingProfileModel("ts-managed-base-fields");

            // then
            assertEquals("ts-managed-base-fields", model.name());
            assertEquals("expected ts description", model.description());
            assertEquals(UUID.fromString(created.getUuid()), model.uuid());
            assertEquals(1, model.version());
            assertFalse(model.enabled());
        }
    }

    @Nested
    class NameUniqueness {

        @Test
        void create_duplicateName_throwsAlreadyExistException()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: first profile created
            signingProfileService.createSigningProfile(buildDelegatedRawRequest("duplicate-name"));

            // when/then
            assertThrows(AlreadyExistException.class,
                    () -> signingProfileService.createSigningProfile(buildDelegatedRawRequest("duplicate-name")));
        }

        @Test
        void update_toExistingNameOfAnotherProfile_throwsAlreadyExistException()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: two profiles
            signingProfileService.createSigningProfile(buildDelegatedRawRequest("profile-alpha"));
            SigningProfileDto beta = signingProfileService.createSigningProfile(buildDelegatedRawRequest("profile-beta"));

            // when: rename beta to alpha
            SigningProfileRequestDto updateRequest = buildDelegatedRawRequest("profile-alpha");

            // then
            assertThrows(AlreadyExistException.class,
                    () -> signingProfileService.updateSigningProfile(
                            SecuredUUID.fromString(beta.getUuid()), updateRequest));
        }

        @Test
        void update_keepingSameName_succeeds()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            SigningProfileDto created = signingProfileService.createSigningProfile(
                    buildDelegatedRawRequest("keep-same-name"));
            SigningProfileRequestDto updateRequest = buildDelegatedRawRequest("keep-same-name");
            updateRequest.setDescription("updated description");

            // when
            SigningProfileDto updated = signingProfileService.updateSigningProfile(
                    SecuredUUID.fromString(created.getUuid()), updateRequest);

            // then
            assertEquals("keep-same-name", updated.getName());
            assertEquals("updated description", updated.getDescription());
        }
    }

    @Nested
    class TimeQualityConfiguration {

        @Test
        void create_timestampingWorkflow_linkedTqcReturnedInDto()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            TimeQualityConfigurationDto tqc = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-for-create-link"));

            TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflow.setTimeQualityConfigurationUuid(UUID.fromString(tqc.getUuid()));

            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("ts-with-tqc");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(workflow);

            // when
            SigningProfileDto dto = signingProfileService.createSigningProfile(request);

            // then: header entity links to TQC
            SigningProfile entity = signingProfileRepository.findById(UUID.fromString(dto.getUuid())).orElseThrow();
            assertNotNull(entity.getTimeQualityConfiguration());
            assertEquals(UUID.fromString(tqc.getUuid()), entity.getTimeQualityConfiguration().getUuid());

            // then: DTO exposes TQC
            TimestampingWorkflowDto tsDto = (TimestampingWorkflowDto) dto.getWorkflow();
            assertNotNull(tsDto.getTimeQualityConfiguration());
            assertEquals(tqc.getUuid(), tsDto.getTimeQualityConfiguration().getUuid());
        }

        @Test
        void create_nonExistentTqcUuid_throwsNotFoundException() {
            // given
            TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflow.setTimeQualityConfigurationUuid(UUID.randomUUID());
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("ts-bad-tqc");
            request.setSigningScheme(new DelegatedSigningRequestDto());
            request.setWorkflow(workflow);

            // when/then
            assertThrows(NotFoundException.class, () -> signingProfileService.createSigningProfile(request));
        }

        @Test
        void update_workflowChangedFromTimestamping_tqcClearedFromHeader()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: profile with TQC linked
            TimeQualityConfigurationDto tqc = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-for-clear-test"));
            TimestampingWorkflowRequestDto timestampingWorkflow = new TimestampingWorkflowRequestDto();
            timestampingWorkflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            timestampingWorkflow.setTimeQualityConfigurationUuid(UUID.fromString(tqc.getUuid()));
            SigningProfileRequestDto createRequest = new SigningProfileRequestDto();
            createRequest.setName("ts-to-raw-profile");
            createRequest.setSigningScheme(new DelegatedSigningRequestDto());
            createRequest.setWorkflow(timestampingWorkflow);
            SigningProfileDto created = signingProfileService.createSigningProfile(createRequest);
            assertNotNull(signingProfileRepository.findById(UUID.fromString(created.getUuid()))
                    .orElseThrow().getTimeQualityConfiguration());

            // when: update to RAW_SIGNING
            SigningProfileRequestDto updateRequest = new SigningProfileRequestDto();
            updateRequest.setName("ts-to-raw-profile");
            updateRequest.setSigningScheme(new DelegatedSigningRequestDto());
            updateRequest.setWorkflow(new RawSigningWorkflowRequestDto());
            signingProfileService.updateSigningProfile(SecuredUUID.fromString(created.getUuid()), updateRequest);

            // then: TQC cleared from header
            assertNull(signingProfileRepository.findById(UUID.fromString(created.getUuid()))
                    .orElseThrow().getTimeQualityConfiguration());
        }

        @Test
        void managedTimestampingModel_withLinkedTqcAndValidateTokenSignature_modelCarriesBothFields()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given
            TimeQualityConfigurationDto tqc = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-for-model-test"));
            StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
            scheme.setCertificateUuid(tsaCertificate.getUuid());
            scheme.setSigningOperationAttributes(List.of(
                    buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                    buildDigestAttribute(DigestAlgorithm.SHA_256)));
            TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflow.setTimeQualityConfigurationUuid(UUID.fromString(tqc.getUuid()));
            workflow.setValidateTokenSignature(true);
            SigningProfileRequestDto request = new SigningProfileRequestDto();
            request.setName("ts-model-tqc-and-validate");
            request.setSigningScheme(scheme);
            request.setWorkflow(workflow);
            signingProfileService.createSigningProfile(request);

            // when
            SigningProfileModel<ManagedTimestampingWorkflow<? extends TimeQualityConfigurationModel>, ?> model =
                    signingProfileService.getManagedTimestampingProfileModel("ts-model-tqc-and-validate");

            // then
            ManagedTimestampingWorkflow<?> wf = model.workflow();
            assertTrue(wf.validateTokenSignature());
            assertInstanceOf(ExplicitTimeQualityConfiguration.class, wf.timeQualityConfiguration());
            assertEquals(UUID.fromString(tqc.getUuid()),
                    ((ExplicitTimeQualityConfiguration) wf.timeQualityConfiguration()).uuid());
        }

        @Test
        void list_returnsAssociatedProfiles()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: 2 profiles linked to the same TQC
            TimeQualityConfigurationDto tqc = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-for-list-test"));
            UUID tqcUuid = UUID.fromString(tqc.getUuid());

            TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
            workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflow.setTimeQualityConfigurationUuid(tqcUuid);
            SigningProfileRequestDto r1 = new SigningProfileRequestDto();
            r1.setName("list-ts-profile-one");
            r1.setSigningScheme(new DelegatedSigningRequestDto());
            r1.setWorkflow(workflow);
            SigningProfileDto p1 = signingProfileService.createSigningProfile(r1);

            TimestampingWorkflowRequestDto workflow2 = new TimestampingWorkflowRequestDto();
            workflow2.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflow2.setTimeQualityConfigurationUuid(tqcUuid);
            SigningProfileRequestDto r2 = new SigningProfileRequestDto();
            r2.setName("list-ts-profile-two");
            r2.setSigningScheme(new DelegatedSigningRequestDto());
            r2.setWorkflow(workflow2);
            SigningProfileDto p2 = signingProfileService.createSigningProfile(r2);

            // when
            List<SimplifiedSigningProfileDto> result = signingProfileService
                    .listSigningProfilesAssociatedTimeQualityConfiguration(
                            SecuredUUID.fromUUID(tqcUuid), SecurityFilter.create());

            // then
            assertEquals(2, result.size());
            List<String> returnedUuids = result.stream().map(SimplifiedSigningProfileDto::getUuid).toList();
            assertTrue(returnedUuids.contains(p1.getUuid()));
            assertTrue(returnedUuids.contains(p2.getUuid()));
        }

        @Test
        void list_emptyWhenNoneAssociated()
                throws AlreadyExistException, AttributeException, NotFoundException {
            // given: TQC with no profiles
            TimeQualityConfigurationDto tqc = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-no-profiles"));

            // when
            List<SimplifiedSigningProfileDto> result = signingProfileService
                    .listSigningProfilesAssociatedTimeQualityConfiguration(
                            SecuredUUID.fromString(tqc.getUuid()), SecurityFilter.create());

            // then
            assertTrue(result.isEmpty());
        }

        @Test
        void list_returnsOnlyProfilesLinkedToSpecificTqc()
                throws AlreadyExistException, AttributeException, ConnectorException, NotFoundException {
            // given: two TQCs, each with one profile
            TimeQualityConfigurationDto tqcA = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-A"));
            TimeQualityConfigurationDto tqcB = timeQualityConfigurationService
                    .createTimeQualityConfiguration(buildTimeQualityConfigurationRequestDto("tqc-B"));

            TimestampingWorkflowRequestDto workflowA = new TimestampingWorkflowRequestDto();
            workflowA.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflowA.setTimeQualityConfigurationUuid(UUID.fromString(tqcA.getUuid()));
            SigningProfileRequestDto reqA = new SigningProfileRequestDto();
            reqA.setName("profile-linked-to-tqc-A");
            reqA.setSigningScheme(new DelegatedSigningRequestDto());
            reqA.setWorkflow(workflowA);
            SigningProfileDto profileA = signingProfileService.createSigningProfile(reqA);

            TimestampingWorkflowRequestDto workflowB = new TimestampingWorkflowRequestDto();
            workflowB.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
            workflowB.setTimeQualityConfigurationUuid(UUID.fromString(tqcB.getUuid()));
            SigningProfileRequestDto reqB = new SigningProfileRequestDto();
            reqB.setName("profile-linked-to-tqc-B");
            reqB.setSigningScheme(new DelegatedSigningRequestDto());
            reqB.setWorkflow(workflowB);
            signingProfileService.createSigningProfile(reqB);

            // when: query for TQC-A
            List<SimplifiedSigningProfileDto> result = signingProfileService
                    .listSigningProfilesAssociatedTimeQualityConfiguration(
                            SecuredUUID.fromString(tqcA.getUuid()), SecurityFilter.create());

            // then: only profile-A returned
            assertEquals(1, result.size());
            assertEquals(profileA.getUuid(), result.getFirst().getUuid());
        }
    }

    /**
     * Builds a minimal valid SigningProfileRequestDto using a DELEGATED scheme and RAW_SIGNING workflow
     * (no foreign-key dependencies on connectors, token profiles, or keys).
     */
    private SigningProfileRequestDto buildDelegatedRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and RAW_SIGNING workflow.
     * Uses the shared MLDSA {@link #cryptographicKey} so no signing-operation-attribute
     * definitions are produced and no attribute content needs to be provided.
     */
    private SigningProfileRequestDto buildManagedStaticKeyRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(certificate.getUuid());
        request.setSigningScheme(scheme);
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a MANAGED/ONE_TIME_KEY scheme and RAW_SIGNING workflow.
     * No FK UUIDs are set, so the request is safe to use against any test database.
     */
    private SigningProfileRequestDto buildManagedOneTimeKeyRawRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new OneTimeKeyManagedSigningRequestDto());
        request.setWorkflow(new RawSigningWorkflowRequestDto());
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and CONTENT_SIGNING workflow.
     */
    private SigningProfileRequestDto buildDelegatedContentRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        ContentSigningWorkflowRequestDto workflow = new ContentSigningWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
        request.setWorkflow(workflow);
        return request;
    }

    /**
     * Builds a request using a DELEGATED scheme and TIMESTAMPING workflow.
     */
    private SigningProfileRequestDto buildDelegatedTimestampingRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        request.setSigningScheme(new DelegatedSigningRequestDto());
        TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
        request.setWorkflow(workflow);
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and TIMESTAMPING workflow,
     * with no additional validation properties set.
     */
    private SigningProfileRequestDto buildManagedStaticKeyTimestampingRequest(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(tsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));
        request.setSigningScheme(scheme);
        TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
        request.setWorkflow(workflow);
        return request;
    }

    /**
     * Builds a request using a MANAGED/STATIC_KEY scheme and TIMESTAMPING workflow
     * with a default policy ID, two allowed policy IDs, and SHA-256 as an allowed digest algorithm.
     */
    private SigningProfileRequestDto buildManagedStaticKeyTimestampingRequestWithValidationProps(String name) {
        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription("Test description for " + name);
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(tsaCertificate.getUuid());
        scheme.setSigningOperationAttributes(List.of(
                buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5),
                buildDigestAttribute(DigestAlgorithm.SHA_256)));
        request.setSigningScheme(scheme);
        TimestampingWorkflowRequestDto wf = new TimestampingWorkflowRequestDto();
        wf.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
        wf.setDefaultPolicyId("1.2.3.4.5");
        wf.setAllowedPolicyIds(List.of("1.2.3.4.5", "1.2.3.4.6"));
        wf.setAllowedDigestAlgorithms(List.of(DigestAlgorithm.SHA_256));
        wf.setValidateTokenSignature(true);
        request.setWorkflow(wf);
        return request;
    }

    /**
     * Persists a SigningRecord that references the given signing profile and version,
     * simulating a signature that was produced using that profile version.
     */
    private void createSigningRecordFor(SigningProfile profile, int version) {
        SigningRecord sig = new SigningRecord();
        sig.setSigningProfile(profile);
        sig.setSigningProfileVersion(version);
        sig.setSigningTime(OffsetDateTime.now());
        signingRecordRepository.save(sig);
    }

    private void attachSelfSignedContent(Certificate cert) throws CertificateException, IOException, NoSuchAlgorithmException, OperatorCreationException {
        X509Certificate x509 = CertificateTestUtil.createCACertificate();
        Certificate entityWithContent = certificateService.createCertificateEntity(x509);
        cert.setCertificateContent(entityWithContent.getCertificateContent());
        cert.setCertificateContentId(entityWithContent.getCertificateContentId());
        certificateRepository.saveAndFlush(cert);
    }

    private TimeQualityConfigurationRequestDto buildTimeQualityConfigurationRequestDto(String name) {
        TimeQualityConfigurationRequestDto req = new TimeQualityConfigurationRequestDto();
        req.setName(name);
        req.setAccuracy(java.time.Duration.ofSeconds(1));
        req.setNtpServers(List.of("pool.ntp.org"));
        req.setNtpCheckInterval(java.time.Duration.ofSeconds(30));
        req.setNtpSamplesPerServer(4);
        req.setNtpCheckTimeout(java.time.Duration.ofSeconds(5));
        req.setNtpServersMinReachable(1);
        req.setMaxClockDrift(java.time.Duration.ofSeconds(1));
        req.setLeapSecondGuard(true);
        return req;
    }

    /**
     * Creates and persists a {@link Certificate} entity that passes eligibility checks for static-key managed signing
     * (ISSUED, VALID, active key with SIGN usage and a token profile assigned) but whose certificate content is signed
     * by an external CA absent from the inventory.
     */
    private Certificate buildIncompleteChainCertificate() throws CertificateException, NoSuchAlgorithmException, OperatorCreationException {
        X509Certificate x509 = CertificateTestUtil.createEndEntityCertificate();
        Certificate cert = new Certificate();
        cert.setKey(cryptographicKey);
        cert.setState(CertificateState.ISSUED);
        cert.setValidationStatus(CertificateValidationStatus.VALID);
        cert = certificateRepository.saveAndFlush(cert);

        Certificate entityWithContent = certificateService.createCertificateEntity(x509);
        cert.setCertificateContent(entityWithContent.getCertificateContent());
        cert.setCertificateContentId(entityWithContent.getCertificateContentId());
        return certificateRepository.saveAndFlush(cert);
    }

    /**
     * Registers a DataAttributeV2 definition in AttributeEngine for the given formatter connector
     * and returns a record holding the generated UUID and name for use in attribute content building.
     */
    private FormatterAttr registerFormatterAttribute(Connector formatter, String label) throws AttributeException {
        UUID attrUuid = UUID.randomUUID();
        String attrName = "data_" + label.replaceAll("\\W+", "_");
        DataAttributeV2 attrDef = new DataAttributeV2();
        attrDef.setUuid(attrUuid.toString());
        attrDef.setName(attrName);
        attrDef.setContentType(AttributeContentType.STRING);
        DataAttributeProperties props = new DataAttributeProperties();
        props.setLabel(label);
        attrDef.setProperties(props);
        attributeEngine.updateDataAttributeDefinitions(formatter.getUuid(), AttributeOperation.WORKFLOW_FORMATTER, List.of(attrDef));
        return new FormatterAttr(attrUuid, attrName);
    }

    private SigningProfile reloadProfile(UUID uuid) {
        return signingProfileRepository.findById(uuid).orElseThrow();
    }

    private SigningProfileVersion loadVersionSnapshot(UUID profileUuid, int version) {
        return signingProfileVersionRepository.findBySigningProfileUuidAndVersion(profileUuid, version).orElseThrow();
    }

    private record FormatterAttr(UUID uuid, String name) {
    }
}
