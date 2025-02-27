package com.trustservice.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SSLCertificateTest {

    private static final String TRUSTSTORE_PATH = "src/test/resources/truststore.p12";
    private static final String TRUSTSTORE_PASSWORD = "TruststorePassword";
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8443;

    private SimpleSSLServer server;

    @BeforeEach
    void setup() {
        server = new SimpleSSLServer();
        server.start();

        // Wait for the server to start properly before running tests
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testServerCertificateValidity() {
        SSLContext sslContext = createSSLContext();
        assertNotNull(sslContext, "SSLContext should not be null");

        try {
            SSLSocketFactory socketFactory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(SERVER_HOST, SERVER_PORT)) {

                socket.startHandshake();
                System.out.println("✅ Connection successful. The server's certificate is trusted.");

                // By default, Java only validates that the certificate is trusted!
                // TODO: activate validation
                // verifyHostname(socket);

                // Send a message to the server
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                writer.write("Hello, SSL Server!\n");
                writer.flush();

                // Read the server response
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String response = reader.readLine();
                System.out.println("📨 Received from server: " + response);

                Assertions.assertEquals("Hello, SSL Client!", response);
            }
        } catch (SSLHandshakeException e) {
            System.err.println("❌ SSL Handshake failed: " + e.getMessage());
            fail("Server certificate or Host is not fully trusted.");
        } catch (IOException e) {
            fail("I/O error while connecting: " + e.getMessage());
        }
    }

    private SSLContext createSSLContext() {
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (var trustStoreStream = new FileInputStream(TRUSTSTORE_PATH)) {
                trustStore.load(trustStoreStream, TRUSTSTORE_PASSWORD.toCharArray());
            }
            System.out.println("✅ Truststore loaded successfully. Entries: " + trustStore.size());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }

    void verifyHostname(SSLSocket socket) throws SSLHandshakeException {
        try {
            String host = socket.getSession().getPeerHost();

            // Get the server's certificate
            X509Certificate cert = (X509Certificate) socket.getSession().getPeerCertificates()[0];

            Collection<List<?>> sanEntries = cert.getSubjectAlternativeNames();

            if (sanEntries != null) {
                for (List<?> entry : sanEntries) {
                    int type = (int) entry.get(0); // Type 2 = DNS Name

                    if (type == 2) {
                        String sanHost = (String) entry.get(1);
                        System.out.println("🔎 Found SAN: " + sanHost);

                        // ✅ Check if the SAN matches the expected hostname
                        if (sanHost.equalsIgnoreCase(host) && sanHost.equalsIgnoreCase(SERVER_HOST)) {
                            System.out.println("✅ Hostname is valid: " + host);
                            return;
                        }
                    }
                }
            }

            // Abort trust due to possible man-in-the-middle
            System.out.println("\uD83D\uDD75\uFE0F Hostname '" + SERVER_HOST + "' not found in server certificate.");
            throw new SSLHandshakeException("❌ Hostname verification failed: " + host + " not in SAN list.");

        } catch (SSLPeerUnverifiedException | CertificateParsingException e) {
            throw new SSLHandshakeException("❌ Host identity cannot be verified.");
        }
    }
}
