package com.example.stockai.stock;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class PublicHttpsUrlValidator {
    private PublicHttpsUrlValidator() {}

    public static URI validate(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("custom provider URL is invalid", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("custom provider URL must use https with a valid host");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("custom provider URL cannot contain credentials, fragments, or a non-HTTPS port");
        }
        String host = uri.getHost();
        if (host.equalsIgnoreCase("localhost") || host.toLowerCase().endsWith(".localhost")) {
            throw new IllegalArgumentException("custom provider URL cannot target localhost");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("custom provider URL must resolve only to public addresses");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("custom provider host cannot be resolved", ex);
        }
        return uri;
    }
}
