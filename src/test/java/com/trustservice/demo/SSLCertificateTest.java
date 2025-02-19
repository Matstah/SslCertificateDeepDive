package com.trustservice.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.Test;

class SSLCertificateTest {

    private static final String TRUSTSTORE_PATH = "src/test/resources/truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "password123"; // Change to TruststorePassword
    private static final String SERVER_HOST = "ubs-local";
    private static final int SERVER_PORT = 8443;

    @Test
    void testServerCertificateValidity() {
        SSLContext sslContext = createSSLContext();
        assertNotNull(sslContext, "SSLContext should not be null");

        try {
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(SERVER_HOST, SERVER_PORT)) {
                socket.startHandshake();
                System.out.println("✅ Connection successful. The server's certificate is trusted.");
            }
        } catch (SSLHandshakeException e) {
            System.err.println("❌ SSL Handshake failed: " + e.getMessage());
            fail("Server certificate is not fully trusted. Possible missing intermediate CA.");
        } catch (IOException e) {
            fail("I/O error while connecting: " + e.getMessage());
        }
    }

    private SSLContext createSSLContext() {
        try {
            // Load the Truststore (containing only the Root CA)
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (var trustStoreStream = Files.newInputStream(Paths.get(TRUSTSTORE_PATH))) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
            }

            // Initialize TrustManagerFactory with the Truststore
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // Set up SSLContext with the TrustManagers
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }
}

