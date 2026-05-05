package com.czertainly.core.util.serialnumber;

import java.net.*;
import java.util.ArrayList;
import java.util.function.Supplier;

final class InstanceIdResolver {

    static final String ENV_VAR = "ILM_INSTANCE_ID";

    enum Source { ENV_VAR, IP_ADDRESS }

    record Resolution(int id, Source source) {}

    private InstanceIdResolver() {
    }

    static Resolution resolve() {
        String envValue = System.getenv(ENV_VAR);
        int id = resolve(envValue, InstanceIdResolver::findLocalAddress);
        Source source = (envValue != null && !envValue.isBlank()) ? Source.ENV_VAR : Source.IP_ADDRESS;
        return new Resolution(id, source);
    }

    private static InetAddress findLocalAddress() {
        try {
            InetAddress localAddress = InetAddress.getLocalHost();
            return isUsableAddress(localAddress) ? localAddress : findUsableAddress();
        } catch (UnknownHostException e) {
            return findUsableAddress();
        }
    }

    private static InetAddress findUsableAddress() {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            var usable = new ArrayList<InetAddress>();
            while (interfaces.hasMoreElements()) {
                var ni = interfaces.nextElement();
                var addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var addr = addresses.nextElement();
                    if (isUsableAddress(addr)) {
                        usable.add(addr);
                    }
                }
            }
            if (usable.isEmpty()) {
                throw new IllegalStateException(
                        "No suitable network address found for instance ID. Set " + ENV_VAR + " explicitly.");
            }
            return usable.stream()
                    .filter(a -> a instanceof Inet4Address)
                    .findFirst()
                    .orElse(usable.getFirst());
        } catch (SocketException e) {
            throw new IllegalStateException(
                    "No suitable network address found for instance ID. Set " + ENV_VAR + " explicitly.", e);
        }
    }

    static boolean isUsableAddress(InetAddress addr) {
        return !addr.isLoopbackAddress()
                && !addr.isLinkLocalAddress()
                && !addr.isMulticastAddress()
                && !addr.isAnyLocalAddress();
    }

    static int resolve(String envValue, Supplier<InetAddress> addressSupplier) {
        if (envValue != null && !envValue.isBlank()) {
            int id;
            try {
                id = Integer.parseInt(envValue.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "ILM_INSTANCE_ID must be a valid integer, got: '" + envValue.strip() + "'");
            }
            if (id < 0 || id > 65535) {
                throw new IllegalArgumentException(
                        "ILM_INSTANCE_ID must be between 0 and 65535, got: " + id);
            }
            return id;
        }

        byte[] address = addressSupplier.get().getAddress();
        return extractIdFromAddress(address);
    }

    static int extractIdFromAddress(byte[] address) {
        // IPv6 address
        if (address.length == 16) {
            // XOR-fold the last 4 bytes into 16 bits. Unlike simple truncation, XOR-folding
            // preserves entropy from both halves of the input. We use only the last 4 bytes
            // because the IPv6 prefix (first 8-12 bytes) is shared across hosts on the same
            // network and adds no distinguishing value.
            int hi = ((address[12] & 0xFF) << 8) | (address[13] & 0xFF);
            int lo = ((address[14] & 0xFF) << 8) | (address[15] & 0xFF);
            return (hi ^ lo) & 0xFFFF;
        }
        // IPv4 address
        return ((address[address.length - 2] & 0xFF) << 8) | (address[address.length - 1] & 0xFF);
    }
}
