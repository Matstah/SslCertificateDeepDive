package com.example.httpsdemo.service;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PartnerConnectionVerificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PartnerConnectionVerificationService.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyManager[] keyManager;
    private final TrustManager[] trustManager;

    public PartnerConnectionVerificationService(
            KeyManager[] keyManagers, TrustManager[] trustManagers) {
        this.keyManager = Arrays.copyOf(keyManagers, keyManagers.length);
        this.trustManager = Arrays.copyOf(trustManagers, trustManagers.length);
    }

    public void verifyTlsConnection(String host, int port) {
        LOGGER.info("Checking connection to {}:{}", host, port);

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        try {
            sslContext.init(keyManager, trustManager, RANDOM);
        } catch (KeyManagementException e) {
            throw new PartnerTlsConnectionException("Failed to initialize SSL connection", e);
        }

        SSLSocketFactory factory = sslContext.getSocketFactory();

        try (SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port)){
            sslSocket.setUseClientMode(true);
            sslSocket.startHandshake();
            HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();
            if (!hostnameVerifier.verify(host, sslSocket.getSession())) {
                throw new PartnerTlsConnectionException("Hostname '" + host + "' verification failed.");
            }
        } catch (IOException e) {
            throw new PartnerTlsConnectionException("Error while establishing TLS connection to " + host + ":" + port + ": " + e.getMessage(), e);
        }
    }

    public static class PartnerTlsConnectionException extends RuntimeException {

        public PartnerTlsConnectionException(String message) {
            super(message);
        }

        public PartnerTlsConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
