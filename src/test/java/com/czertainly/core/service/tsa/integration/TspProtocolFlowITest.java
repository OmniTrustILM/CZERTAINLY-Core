package com.czertainly.core.service.tsa.integration;

import com.czertainly.api.model.client.connector.v2.ConnectorInterface;
import com.czertainly.api.model.client.connector.v2.FeatureFlag;
import com.czertainly.api.model.client.signing.profile.SigningProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.scheme.StaticKeyManagedSigningRequestDto;
import com.czertainly.api.model.client.signing.protocols.tsp.TspProfileRequestDto;
import com.czertainly.api.model.client.signing.profile.workflow.TimestampingWorkflowRequestDto;
import com.czertainly.api.model.common.attribute.common.content.AttributeContentType;
import com.czertainly.api.model.common.attribute.v2.content.StringAttributeContentV2;
import com.czertainly.api.model.common.enums.cryptography.DigestAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.common.enums.cryptography.KeyType;
import com.czertainly.api.model.common.enums.cryptography.RsaSignatureScheme;
import com.czertainly.core.attribute.EcdsaSignatureAttributes;
import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.client.attribute.RequestAttributeV2;
import com.czertainly.api.model.connector.cryptography.enums.TokenInstanceStatus;
import com.czertainly.api.model.connector.signatures.formatter.FormatDtbsResponseDto;
import com.czertainly.api.model.connector.signatures.formatter.FormattedResponseDto;
import com.czertainly.api.model.core.certificate.CertificateState;
import com.czertainly.api.model.core.certificate.CertificateValidationStatus;
import com.czertainly.api.model.core.connector.ConnectorStatus;
import com.czertainly.api.model.core.oid.SystemOid;
import com.czertainly.api.model.client.connector.v2.ConnectorVersion;
import com.czertainly.core.attribute.RsaSignatureAttributes;
import com.czertainly.core.dao.entity.Certificate;
import com.czertainly.core.dao.entity.CertificateContent;
import com.czertainly.core.dao.entity.Connector;
import com.czertainly.core.dao.entity.ConnectorInterfaceEntity;
import com.czertainly.core.dao.entity.CryptographicKey;
import com.czertainly.core.dao.entity.CryptographicKeyItem;
import com.czertainly.core.dao.entity.TokenInstanceReference;
import com.czertainly.core.dao.entity.TokenProfile;
import com.czertainly.core.dao.repository.CertificateContentRepository;
import com.czertainly.core.dao.repository.CertificateRepository;
import com.czertainly.core.dao.repository.ConnectorInterfaceRepository;
import com.czertainly.core.dao.repository.ConnectorRepository;
import com.czertainly.core.dao.repository.CryptographicKeyItemRepository;
import com.czertainly.core.dao.repository.CryptographicKeyRepository;
import com.czertainly.core.dao.repository.TokenInstanceReferenceRepository;
import com.czertainly.core.dao.repository.TokenProfileRepository;
import com.czertainly.core.api.tsp.TspControllerImpl;
import com.czertainly.core.service.SigningProfileService;
import com.czertainly.core.service.TspProfileService;
import com.czertainly.core.service.tsa.TimestampTokenTestUtil;
import com.czertainly.core.util.BaseSpringBootTest;
import com.czertainly.core.util.CertificateTestUtil;
import com.czertainly.core.util.CertificateUtil;
import com.czertainly.core.util.MetaDefinitions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.jcajce.spec.MLDSAParameterSpec;
import org.bouncycastle.jcajce.spec.SLHDSAParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * End-to-end integration test for the RFC 3161 Timestamp Protocol implementation.
 *
 * <p>Exercises the full timestamp-token production path via the service layer:
 * <ol>
 *   <li>Infrastructure setup – connector, token instance, token profile, cryptographic key,
 *       TSA certificate, signing profile, TSP profile</li>
 *   <li>TSP request construction (BouncyCastle {@link TimeStampRequestGenerator})</li>
 *   <li>{@link TspControllerImpl#timestamp} invocation</li>
 *   <li>RFC 3161 {@link TimeStampResponse} parsing and PKI status assertion</li>
 * </ol>
 *
 * <p>External connectors are stubbed with WireMock.
 */
public class TspProtocolFlowITest extends BaseSpringBootTest {

    // ── Spring beans ──────────────────────────────────────────────────────────

    @Autowired
    private TspControllerImpl tspController;
    @Autowired
    private SigningProfileService signingProfileService;
    @Autowired
    private TspProfileService tspProfileService;

    @Autowired
    private ConnectorRepository connectorRepository;
    @Autowired
    private ConnectorInterfaceRepository connectorInterfaceRepository;
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
    private CertificateContentRepository certificateContentRepository;

    // ── Algorithm configuration ───────────────────────────────────────────────

    /**
     * Static description of a single signing algorithm under test.
     *
     * <p>{@code paramSpec} is {@code null} for RSA (which uses a plain key-size integer instead);
     * all other algorithms supply an {@link AlgorithmParameterSpec}.
     * {@code keyItemLength} is {@code 0} for post-quantum algorithms that have no numeric key size.
     */
    private record AlgorithmSpec(
            String label,
            KeyAlgorithm keyAlgorithm,
            String jcaKeyAlgorithm,
            AlgorithmParameterSpec paramSpec,
            int keySize,
            String certAndTokenSigAlg,
            String formatterUrlPath,
            int keyItemLength
    ) {}

    private static final List<AlgorithmSpec> ALGORITHM_SPECS = List.of(
            new AlgorithmSpec("RSA",               KeyAlgorithm.RSA,    "RSA",     null,                          2048, "SHA256withRSA",     "/formatter-rsa",    2048),
            new AlgorithmSpec("ECDSA",             KeyAlgorithm.ECDSA,  "EC",      new ECGenParameterSpec("secp256r1"),0, "SHA256withECDSA",   "/formatter-ecdsa",  256),
            new AlgorithmSpec("FALCON-1024",       KeyAlgorithm.FALCON, "FALCON",  FalconParameterSpec.falcon_1024,             0, "FALCON-1024",       "/formatter-falcon", 0),
            new AlgorithmSpec("ML-DSA-65",         KeyAlgorithm.MLDSA,  "ML-DSA",  MLDSAParameterSpec.ml_dsa_65,                0, "ML-DSA-65",         "/formatter-mldsa",  0),
            new AlgorithmSpec("SLH-DSA-SHA2-128F", KeyAlgorithm.SLHDSA, "SLH-DSA", SLHDSAParameterSpec.slh_dsa_sha2_128f,       0, "SLH-DSA-SHA2-128F", "/formatter-slhdsa", 0)
    );

    // ── Per-test state ────────────────────────────────────────────────────────

    private WireMockServer wireMockServer;
    private UniversalSignerTransformer signerTransformer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TokenInstanceReference tokenInstance;
    private TokenProfile tokenProfile;

    /** Key pairs indexed by algorithm — populated in {@link #setUp()}. */
    private final Map<KeyAlgorithm, KeyPair> keyPairs = new EnumMap<>(KeyAlgorithm.class);
    /** Self-signed TSA certificates indexed by algorithm — populated in {@link #setUp()}. */
    private final Map<KeyAlgorithm, X509Certificate> tsaCerts = new EnumMap<>(KeyAlgorithm.class);
    /** Pre-computed RFC 3161 TimeStampToken DER bytes indexed by algorithm — populated in {@link #setUp()}. */
    private final Map<KeyAlgorithm, byte[]> precomputedTokens = new EnumMap<>(KeyAlgorithm.class);
    /** Persisted Certificate entities indexed by algorithm — populated in {@link #buildSharedInfrastructure()}. */
    private final Map<KeyAlgorithm, Certificate> dbCertificates = new EnumMap<>(KeyAlgorithm.class);
    /** Formatter connectors indexed by algorithm — populated in {@link #buildSharedInfrastructure()}. */
    private final Map<KeyAlgorithm, Connector> formatterConnectors = new EnumMap<>(KeyAlgorithm.class);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    public void setUp() throws Exception {
        ensureBouncyCastleProvider();

        for (AlgorithmSpec spec : ALGORITHM_SPECS) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(spec.jcaKeyAlgorithm(), BouncyCastleProvider.PROVIDER_NAME);
            if (spec.paramSpec() != null) {
                gen.initialize(spec.paramSpec());
            } else {
                gen.initialize(spec.keySize());
            }
            KeyPair kp = gen.generateKeyPair();
            keyPairs.put(spec.keyAlgorithm(), kp);
            X509Certificate cert = CertificateTestUtil.createTimestampingCertificate(kp, spec.certAndTokenSigAlg());
            tsaCerts.put(spec.keyAlgorithm(), cert);
            precomputedTokens.put(spec.keyAlgorithm(),
                    TimestampTokenTestUtil.createTimestampTokenSignedWith(kp, cert, spec.certAndTokenSigAlg()).getEncoded());
        }

        wireMockServer = new WireMockServer(
                WireMockConfiguration.options()
                        .port(0)
                        .extensions(signerTransformer = new UniversalSignerTransformer()));
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        stubSignEndpoints();
        stubFormatterEndpoints();
        buildSharedInfrastructure();
    }

    @AfterEach
    public void tearDown() {
        wireMockServer.stop();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * Parameterized end-to-end flow without token signature validation.
     *
     * <p>For each supported signing algorithm (RSA, ECDSA, FALCON-1024, ML-DSA-65, SLH-DSA-SHA2-128F):
     * asserts that the controller returns PKI status GRANTED with a SHA-256 imprint algorithm.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allSigningAlgorithmParameters")
    public void withoutSignatureValidation(String label, KeyAlgorithm keyAlgorithm) throws Exception {
        runTimestampFlow(label, keyAlgorithm, false);
    }

    /**
     * Parameterized end-to-end flow with token signature validation enabled.
     *
     * <p>For each supported signing algorithm (RSA, ECDSA, FALCON-1024, ML-DSA-65, SLH-DSA-SHA2-128F):
     * asserts that the controller returns PKI status GRANTED with a SHA-256 imprint algorithm,
     * after verifying the cryptographic signature on the timestamp token.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("allSigningAlgorithmParameters")
    public void withSignatureValidation(String label, KeyAlgorithm keyAlgorithm) throws Exception {
        runTimestampFlow(label, keyAlgorithm, true);
    }

    /**
     * Parameter source for {@link #withoutSignatureValidation} and {@link #withSignatureValidation}.
     *
     * <p>Each entry is {@code (label, KeyAlgorithm)}, derived directly from {@link #ALGORITHM_SPECS}.
     * All key material is pre-generated in {@link #setUp()} — no per-invocation generation occurs.
     */
    static Stream<Arguments> allSigningAlgorithmParameters() {
        return ALGORITHM_SPECS.stream().map(s -> Arguments.of(s.label(), s.keyAlgorithm()));
    }

    private void runTimestampFlow(String label, KeyAlgorithm keyAlgorithm, boolean validateTokenSignature) throws Exception {
        String profileSuffix = validateTokenSignature ? "-validated" : "";
        String tspProfileName = "testTspProfile-" + label + profileSuffix;

        UUID profileUuid = createSigningProfile(
                keyAlgorithm,
                dbCertificates.get(keyAlgorithm),
                formatterConnectors.get(keyAlgorithm),
                "testSigningProfile-" + label + profileSuffix,
                "TSP integration test signing profile: " + label,
                validateTokenSignature);
        createTspProfile(tspProfileName, "TSP integration test profile: " + label, profileUuid);

        assertGrantedSha256Response(tspController.timestamp(tspProfileName, buildSha256TspRequestBytes()));
    }

    private void buildSharedInfrastructure() throws Exception {
        Connector connector = persistConnector();
        tokenInstance = persistTokenInstance(connector);
        tokenProfile = persistTokenProfile(tokenInstance);

        for (AlgorithmSpec spec : ALGORITHM_SPECS) {
            // Persist formatter connector and register its formatResponse stub
            Connector fc = persistFormatterConnector(spec);
            formatterConnectors.put(spec.keyAlgorithm(), fc);

            FormattedResponseDto tokenResponse = new FormattedResponseDto();
            tokenResponse.setResponse(precomputedTokens.get(spec.keyAlgorithm()));
            wireMockServer.stubFor(
                    post(urlPathMatching(spec.formatterUrlPath() + "/v1/signatureProvider/formatting/formatResponse"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(objectMapper.writeValueAsString(tokenResponse)))
            );

            // Persist cryptographic key, register it with the signer transformer, then persist the certificate
            CryptographicKey key = persistCryptographicKey(spec, tokenInstance, tokenProfile, keyPairs.get(spec.keyAlgorithm()));
            UUID privKeyRefUuid = cryptographicKeyItemRepository.findByKeyUuidIn(List.of(key.getUuid())).stream()
                    .filter(i -> i.getType() == KeyType.PRIVATE_KEY)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No private key item for " + spec.label()))
                    .getKeyReferenceUuid();
            signerTransformer.registerKey(privKeyRefUuid, keyPairs.get(spec.keyAlgorithm()).getPrivate(), spec.certAndTokenSigAlg());

            dbCertificates.put(spec.keyAlgorithm(), persistTsaCertificate(key, tsaCerts.get(spec.keyAlgorithm())));
        }
    }

    /** Stubs POST /v1/cryptographyProvider/tokens/{any}/keys/{any}/sign → 200 via the real-signer transformer. */
    private void stubSignEndpoints() {
        wireMockServer.stubFor(
                post(urlPathMatching("/v1/cryptographyProvider/tokens/.+/keys/.+/sign"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withTransformers("real-signer"))
        );
    }

    /**
     * Stubs the shared phase-1 formatter endpoints (formatDtbs, attributes) for all algorithm URL prefixes.
     * Per-algorithm phase-2 (formatResponse) stubs are registered in {@link #buildSharedInfrastructure()}.
     */
    private void stubFormatterEndpoints() throws Exception {        FormatDtbsResponseDto dtbsResponse = new FormatDtbsResponseDto();
        dtbsResponse.setDtbs(new byte[]{1, 2, 3, 4, 5});
        String dtbsJson = objectMapper.writeValueAsString(dtbsResponse);
        wireMockServer.stubFor(
                post(urlPathMatching("/formatter-.*/v1/signatureProvider/formatting/formatDtbs"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(dtbsJson))
        );
        wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathMatching("/formatter-.*/v1/signatureProvider/formatting/attributes"))
                        .willReturn(WireMock.okJson("[]"))
        );
    }

    private Connector persistConnector() {
        Connector connector = new Connector();
        connector.setName("tsp-crypto-connector");
        connector.setUrl("http://localhost:" + wireMockServer.port());
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        return connectorRepository.save(connector);
    }

    private Connector persistFormatterConnector(AlgorithmSpec spec) {
        Connector connector = new Connector();
        connector.setName("tsp-formatter-connector-" + spec.label().toLowerCase());
        connector.setUrl("http://localhost:" + wireMockServer.port() + spec.formatterUrlPath());
        connector.setVersion(ConnectorVersion.V1);
        connector.setStatus(ConnectorStatus.CONNECTED);
        connector = connectorRepository.save(connector);

        ConnectorInterfaceEntity connectorInterface = new ConnectorInterfaceEntity();
        connectorInterface.setConnectorUuid(connector.getUuid());
        connectorInterface.setInterfaceCode(ConnectorInterface.SIGNATURE_FORMATTING);
        connectorInterface.setVersion("1.0.0");
        connectorInterface.setFeatures(List.of(FeatureFlag.TIMESTAMPING));
        connectorInterfaceRepository.save(connectorInterface);

        return connector;
    }

    private TokenInstanceReference persistTokenInstance(Connector connector) {
        TokenInstanceReference ref = new TokenInstanceReference();
        ref.setName("tsp-token-instance");
        ref.setTokenInstanceUuid(UUID.randomUUID().toString());
        ref.setConnector(connector);
        ref.setStatus(TokenInstanceStatus.CONNECTED);
        return tokenInstanceReferenceRepository.saveAndFlush(ref);
    }

    private TokenProfile persistTokenProfile(TokenInstanceReference tokenInstance) {
        TokenProfile profile = new TokenProfile();
        profile.setName("tsp-token-profile");
        profile.setTokenInstanceReference(tokenInstance);
        profile.setTokenInstanceName(tokenInstance.getName());
        profile.setEnabled(true);
        return tokenProfileRepository.saveAndFlush(profile);
    }

    /**
     * Persists a {@link CryptographicKey} with private and public key items for the given algorithm.
     * The public-key {@code keyData} (base64 SubjectPublicKeyInfo) is required by
     * {@link com.czertainly.core.util.CryptographyUtil#resolveSignatureAlgorithmName} to derive
     * the algorithm name from the embedded OID for post-quantum keys.
     */
    private CryptographicKey persistCryptographicKey(AlgorithmSpec spec,
                                                     TokenInstanceReference tokenInstance,
                                                     TokenProfile tokenProfile,
                                                     KeyPair keyPair) {
        CryptographicKey key = new CryptographicKey();
        key.setName("tsp-key-" + spec.label().toLowerCase());
        key.setTokenProfile(tokenProfile);
        key.setTokenInstanceReference(tokenInstance);
        key = cryptographicKeyRepository.saveAndFlush(key);

        CryptographicKeyItem privateItem = new CryptographicKeyItem();
        privateItem.setKey(key);
        privateItem.setKeyUuid(key.getUuid());
        privateItem.setType(KeyType.PRIVATE_KEY);
        privateItem.setState(com.czertainly.api.model.core.cryptography.key.KeyState.ACTIVE);
        privateItem.setEnabled(true);
        privateItem.setKeyAlgorithm(spec.keyAlgorithm());
        privateItem.setLength(spec.keyItemLength());
        privateItem.setUsage(List.of(com.czertainly.api.model.core.cryptography.key.KeyUsage.SIGN));
        privateItem = cryptographicKeyItemRepository.saveAndFlush(privateItem);
        privateItem.setKeyReferenceUuid(privateItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(privateItem);

        String pubKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        CryptographicKeyItem publicItem = new CryptographicKeyItem();
        publicItem.setKey(key);
        publicItem.setKeyUuid(key.getUuid());
        publicItem.setType(KeyType.PUBLIC_KEY);
        publicItem.setState(com.czertainly.api.model.core.cryptography.key.KeyState.ACTIVE);
        publicItem.setEnabled(true);
        publicItem.setKeyAlgorithm(spec.keyAlgorithm());
        publicItem.setLength(spec.keyItemLength());
        publicItem.setUsage(List.of(com.czertainly.api.model.core.cryptography.key.KeyUsage.SIGN));
        publicItem.setKeyData(pubKeyBase64);
        publicItem = cryptographicKeyItemRepository.saveAndFlush(publicItem);
        publicItem.setKeyReferenceUuid(publicItem.getUuid());
        cryptographicKeyItemRepository.saveAndFlush(publicItem);

        return cryptographicKeyRepository.findById(key.getUuid()).orElseThrow();
    }

    /**
     * Persists a Certificate entity with the TSA X.509 certificate content and all conditions required by
     * {@code isCertificateDigitalSigningAcceptable} for TIMESTAMPING:
     * <ul>
     *   <li>state = ISSUED, validationStatus = VALID</li>
     *   <li>key has a token profile</li>
     *   <li>extendedKeyUsage = [id-kp-timeStamping], critical = true</li>
     * </ul>
     */
    private Certificate persistTsaCertificate(CryptographicKey key, X509Certificate x509) throws CertificateEncodingException, NoSuchAlgorithmException {
        // Persist certificate content (base64-encoded DER without PEM headers, matching normalizeCertificateContent)
        String derBase64 = Base64.getEncoder().encodeToString(x509.getEncoded());
        String fingerprint = CertificateUtil.getThumbprint(x509.getEncoded());

        CertificateContent content = new CertificateContent();
        content.setContent(derBase64);
        content.setFingerprint(fingerprint);
        content = certificateContentRepository.saveAndFlush(content);

        Certificate cert = new Certificate();
        cert.setKey(key);
        cert.setState(CertificateState.ISSUED);
        cert.setValidationStatus(CertificateValidationStatus.VALID);
        cert.setFingerprint(fingerprint);
        cert.setCertificateContent(content);
        // RFC 3161: exactly id-kp-timeStamping, critical
        cert.setExtendedKeyUsage(MetaDefinitions.serializeArrayString(List.of(SystemOid.TIME_STAMPING.getOid())));
        cert.setExtendedKeyUsageCritical(true);
        return certificateRepository.saveAndFlush(cert);
    }

    /**
     * Creates a signing profile for the given algorithm.
     *
     * <p>RSA profiles carry PKCS#1 v1.5 scheme and SHA-256 digest attributes; ECDSA profiles
     * carry a SHA-256 digest attribute; post-quantum profiles carry no attributes (the algorithm
     * is derived from the public key's SubjectPublicKeyInfo OID at signing time).
     */
    private UUID createSigningProfile(KeyAlgorithm keyAlgorithm,
                                      Certificate certificate,
                                      Connector formatterConnector,
                                      String name,
                                      String description,
                                      boolean validateTokenSignature) throws Exception {
        StaticKeyManagedSigningRequestDto scheme = new StaticKeyManagedSigningRequestDto();
        scheme.setCertificateUuid(certificate.getUuid());
        scheme.setSigningOperationAttributes(buildSigningAttributes(keyAlgorithm));

        TimestampingWorkflowRequestDto workflow = new TimestampingWorkflowRequestDto();
        workflow.setSignatureFormatterConnectorUuid(formatterConnector.getUuid());
        workflow.setQualifiedTimestamp(false);
        workflow.setDefaultPolicyId("1.2.3.4.5");
        workflow.setValidateTokenSignature(validateTokenSignature);

        SigningProfileRequestDto request = new SigningProfileRequestDto();
        request.setName(name);
        request.setDescription(description);
        request.setSigningScheme(scheme);
        request.setWorkflow(workflow);

        return UUID.fromString(signingProfileService.createSigningProfile(request).getUuid());
    }

    /** Returns the signing-operation attributes required for {@code keyAlgorithm}'s profile. */
    private static List<RequestAttribute> buildSigningAttributes(KeyAlgorithm keyAlgorithm) {
        return switch (keyAlgorithm) {
            case RSA -> List.of(buildRsaSchemeAttribute(RsaSignatureScheme.PKCS1_v1_5), buildDigestAttribute(DigestAlgorithm.SHA_256));
            case ECDSA -> List.of(buildEcdsaDigestAttribute(DigestAlgorithm.SHA_256));
            default -> List.of(); // PQ: algorithm derived from SubjectPublicKeyInfo OID
        };
    }

    private void createTspProfile(String name, String description, UUID defaultSigningProfileUuid) throws Exception {
        TspProfileRequestDto request = new TspProfileRequestDto();
        request.setName(name);
        request.setDescription(description);
        request.setDefaultSigningProfileUuid(defaultSigningProfileUuid);

        tspProfileService.createTspProfile(request);
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /**
     * Builds an SHA-256 TSP request over a fixed test imprint, with certReq=true.
     */
    private static byte[] buildSha256TspRequestBytes() throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("Hello, Timestamp!".getBytes());
        TimeStampRequestGenerator gen = new TimeStampRequestGenerator();
        gen.setCertReq(true);
        return gen.generate(TSPAlgorithms.SHA256, hash, BigInteger.valueOf(System.currentTimeMillis())).getEncoded();
    }

    /**
     * Asserts HTTP 200, PKIStatus GRANTED, token present, and SHA-256 imprint algorithm.
     */
    private static void assertGrantedSha256Response(ResponseEntity<byte[]> response) throws Exception {
        Assertions.assertEquals(200, response.getStatusCode().value());
        byte[] responseBytes = response.getBody();
        Assertions.assertNotNull(responseBytes);
        Assertions.assertTrue(responseBytes.length > 0, "Response body must not be empty");

        TimeStampResponse tsResponse = new TimeStampResponse(responseBytes);
        Assertions.assertEquals(PKIStatus.GRANTED, tsResponse.getStatus(),
                "Expected PKIStatus GRANTED (0) but got: " + tsResponse.getStatus() + " - " + tsResponse.getStatusString()
        );

        Assertions.assertNotNull(tsResponse.getTimeStampToken(), "TimeStampToken must be present");
        String imprintAlg = tsResponse.getTimeStampToken().getTimeStampInfo().getMessageImprintAlgOID().getId();
        Assertions.assertEquals(TSPAlgorithms.SHA256.getId(), imprintAlg,
                "Message imprint algorithm must be SHA-256");
    }

    // ── Crypto helpers ────────────────────────────────────────────────────────

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    // ── WireMock transformer ──────────────────────────────────────────────────

    /**
     * WireMock extension that routes each sign request to the correct private key by extracting
     * the key-reference UUID from the request URL.
     */
    private static class UniversalSignerTransformer implements ResponseDefinitionTransformerV2 {

        private static final Pattern KEY_REF_UUID_PATTERN = Pattern.compile("/keys/([^/]+)/sign");

        private final Map<String, KeyEntry> keysByReferenceUuid = new ConcurrentHashMap<>();

        private record KeyEntry(PrivateKey privateKey, String algorithmName) {}

        void registerKey(UUID keyReferenceUuid, PrivateKey privateKey, String algorithmName) {
            keysByReferenceUuid.put(keyReferenceUuid.toString(), new KeyEntry(privateKey, algorithmName));
        }

        @Override
        public ResponseDefinition transform(ServeEvent serveEvent) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode body = objectMapper.readTree(serveEvent.getRequest().getBodyAsString());
                byte[] dtbs = Base64.getDecoder().decode(body.at("/data/0/data").asText());

                String url = serveEvent.getRequest().getUrl();
                Matcher matcher = KEY_REF_UUID_PATTERN.matcher(url);
                if (!matcher.find()) {
                    throw new IllegalStateException("No key reference UUID in sign URL: " + url);
                }
                KeyEntry entry = keysByReferenceUuid.get(matcher.group(1));
                if (entry == null) {
                    throw new IllegalStateException("No registered key for reference UUID: " + matcher.group(1));
                }

                Signature sig = Signature.getInstance(entry.algorithmName(), BouncyCastleProvider.PROVIDER_NAME);
                sig.initSign(entry.privateKey());
                sig.update(dtbs);
                byte[] signature = sig.sign();

                String responseBody = objectMapper.writeValueAsString(
                        new SignDataConnectorResponse(List.of(new SignatureEntry(signature))));

                return ResponseDefinitionBuilder.responseDefinition()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)
                        .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute signature in WireMock transformer", e);
            }
        }

        @Override
        public String getName() { return "real-signer"; }

        @Override
        public boolean applyGlobally() { return false; }
    }

    // ── Attribute builders ────────────────────────────────────────────────────

    private static RequestAttributeV2 buildRsaSchemeAttribute(RsaSignatureScheme scheme) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_RSA_SIG_SCHEME);
        attr.setContentType(AttributeContentType.STRING);
        attr.setContent(List.of(new StringAttributeContentV2(scheme.getLabel(), scheme.getCode())));
        return attr;
    }

    private static RequestAttributeV2 buildDigestAttribute(DigestAlgorithm algorithm) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST_UUID));
        attr.setName(RsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST);
        attr.setContentType(AttributeContentType.STRING);
        attr.setContent(List.of(new StringAttributeContentV2(algorithm.getLabel(), algorithm.getCode())));
        return attr;
    }

    private static RequestAttributeV2 buildEcdsaDigestAttribute(DigestAlgorithm algorithm) {
        RequestAttributeV2 attr = new RequestAttributeV2();
        attr.setUuid(UUID.fromString(EcdsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST_UUID));
        attr.setName(EcdsaSignatureAttributes.ATTRIBUTE_DATA_SIG_DIGEST);
        attr.setContentType(AttributeContentType.STRING);
        attr.setContent(List.of(new StringAttributeContentV2(algorithm.getLabel(), algorithm.getCode())));
        return attr;
    }

    // ── WireMock response POJOs ───────────────────────────────────────────────

    /**
     * Matches the connector-side {@code SignDataResponseDto} JSON structure.
     */
    record SignDataConnectorResponse(List<SignatureEntry> signatures) {
    }

    /**
     * Matches the connector-side {@code SignatureResponseData} JSON structure.
     */
    record SignatureEntry(byte[] data) {
    }
}
