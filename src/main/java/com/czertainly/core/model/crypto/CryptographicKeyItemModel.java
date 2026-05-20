package com.czertainly.core.model.crypto;

import com.czertainly.api.model.common.enums.cryptography.KeyAlgorithm;
import com.czertainly.api.model.core.cryptography.key.KeyState;
import com.czertainly.api.model.core.cryptography.key.KeyUsage;

import java.util.List;
import java.util.UUID;

public record CryptographicKeyItemModel(
        UUID keyItemUuid,
        KeyState state,
        boolean enabled,
        List<KeyUsage> usage,
        KeyAlgorithm keyAlgorithm,
        UUID keyReferenceUuid,
        UUID connectorUuid,
        UUID tokenInstanceUuid
) {}
