/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.findatex.validator.external.proxy;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Custom Authenticator for HTTP/HTTPS proxy authentication.
 *
 * <p>This authenticator provides username/password credentials when connecting
 * through a corporate proxy that requires authentication. It is designed to work
 * with Java's {@link java.net.HttpURLConnection} when connecting through a proxy
 * that has been configured with {@link java.net.Proxy}.
 *
 * <p><strong>Security Features:</strong>
 * <ul>
 *   <li>Password stored as {@code char[]} instead of {@code String} to allow clearing from memory</li>
 *   <li>Only responds to {@code RequestorType.PROXY} requests to prevent credential leakage to web servers</li>
 *   <li>Only answers challenges from the configured proxy host/port, so a different
 *       proxy (e.g. one a PAC/WPAD script selected) never receives the credentials</li>
 *   <li>Passwords are never logged or included in debug output</li>
 *   <li>Supports clearing credentials from memory after use</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * ProxyAuthenticator authenticator = new ProxyAuthenticator("proxy.corp", 8080, "proxyuser", "proxypass");
 * Authenticator.setDefault(authenticator);
 *
 * // Now HttpURLConnection will use these credentials for proxy authentication
 * URL url = new URL("https://api.github.com");
 * HttpURLConnection conn = (HttpURLConnection) url.openConnection(proxy);
 * // ...
 *
 * // Clear credentials from memory when done
 * authenticator.clear();
 * }</pre>
 *
 * @since 1.2.3
 */
public class ProxyAuthenticator extends Authenticator {
    private static final Logger logger = LoggerFactory.getLogger(ProxyAuthenticator.class);

    private final String proxyHost;
    private final int proxyPort;
    private final String username;
    private final char[] password;

    /**
     * Creates a new ProxyAuthenticator bound to one proxy.
     *
     * @param proxyHost the configured proxy host the credentials belong to
     * @param proxyPort the configured proxy port
     * @param username the proxy username (not null, but may be empty)
     * @param password the proxy password (may be null)
     */
    public ProxyAuthenticator(String proxyHost, int proxyPort, String username, String password) {
        this.proxyHost = proxyHost == null ? "" : proxyHost.trim();
        this.proxyPort = proxyPort;
        this.username = username;
        this.password = password != null ? password.toCharArray() : new char[0];
    }

    /**
     * Called by HttpURLConnection when authentication is required.
     *
     * <p>This method only provides credentials for proxy authentication requests
     * ({@code RequestorType.PROXY}). For all other request types, it returns
     * {@code null} to allow other authenticators to handle the request.
     *
     * @return PasswordAuthentication with proxy credentials for proxy requests,
     *         or null for non-proxy requests
     */
    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        // Only provide credentials for proxy authentication
        if (getRequestorType() == RequestorType.PROXY) {
            if (!isConfiguredProxy(getRequestingHost(), getRequestingPort())) {
                // Basic is not disabled for tunnels (NTLM needs the scheme list
                // emptied), so a stray proxy would get the password in clear text.
                logger.warn("Proxy authentication requested by {}:{}, which is not the configured "
                        + "proxy — credentials withheld", getRequestingHost(), getRequestingPort());
                return null;
            }
            logger.debug("Providing proxy authentication for: {}:{}",
                    getRequestingHost(), getRequestingPort());
            // DO NOT log username or password!
            return new PasswordAuthentication(username, password);
        }

        // Not a proxy request - return null to let other authenticators handle it
        // This prevents credential leakage to non-proxy HTTP servers
        logger.debug("Authenticator called for non-proxy request (type: {}), returning null",
                getRequestorType());
        return null;
    }

    boolean isConfiguredProxy(String host, int port) {
        return host != null && host.equalsIgnoreCase(proxyHost) && port == proxyPort;
    }

    /**
     * Clears credentials from memory.
     *
     * <p>Call this after the connection is complete to minimize security exposure.
     * This method overwrites the password array with zeros to prevent the actual
     * password from being recoverable from memory.
     */
    public void clear() {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
