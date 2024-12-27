package com.example.httpsdemo.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PartnerConnectionVerificationServiceTest {

    PartnerConnectionVerificationService cut;
    TrustManagerFactory trustManagerFactory;
    KeyManagerFactory clientKeyManagerFactory;
    KeyManagerFactory localhostKeyManagerFactory;
    KeyManagerFactory untrustedLocalhostKeyManagerFactory;
    KeyManagerFactory fakehostKeyManagerFactory;

    @BeforeEach
    void beforeEach() throws Exception {

        localhostKeyManagerFactory = KeyManagerFactory.getInstance("PKIX");
        localhostKeyManagerFactory.init(loadKeyStore("localhost-keystore.p12"), "password".toCharArray());

        trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init(loadKeyStore("localhost-keystore.p12"));

        clientKeyManagerFactory = KeyManagerFactory.getInstance("PKIX");
        clientKeyManagerFactory.init(loadKeyStore("client-keystore.p12"), "password".toCharArray());
 
        trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        trustManagerFactory.init(loadKeyStore("truststore.p12"));

        untrustedLocalhostKeyManagerFactory = KeyManagerFactory.getInstance("PKIX");
        untrustedLocalhostKeyManagerFactory.init(loadKeyStore("untrusted-localhost-keystore.p12"),
                "password".toCharArray());

        fakehostKeyManagerFactory = KeyManagerFactory.getInstance("PKIX");
        fakehostKeyManagerFactory.init(loadKeyStore("fakehost-keystore.p12"), "password".toCharArray());

        cut = new PartnerConnectionVerificationService(clientKeyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers());
    }

    private KeyStore loadKeyStore(String resourceName) {
        try {
            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(getClass().getResourceAsStream(resourceName), "password".toCharArray());
            Certificate[] certificates = keyStore.getCertificateChain("localhost");
            
            return keyStore;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void verifyTlsConnection_doesNotThrowException() throws Exception {
        try (PartnerServer server = PartnerServer.start(localhostKeyManagerFactory, trustManagerFactory)) {
            cut.verifyTlsConnection("loaclhost", server.getLocalPort());
        }
    }

    @Test
    void verifyTlsConnection_failsOnHostnameVerificationFailure() throws Exception {
        try (PartnerServer server = PartnerServer.start(fakehostKeyManagerFactory, trustManagerFactory)) {
            assertThatThrownBy(() -> cut.verifyTlsConnection("localhost", server.getLocalPort()))
                    .isInstanceOf(PartnerConnectionVerificationService.PartnerTlsConnectionException.class)
                    .hasMessageContaining("Hostname 'localhost' verification failed");
        }
    }

    @Test
    void verifyTlsConnection_failsOnUntrustedCaCertificate() throws Exception {
        try (PartnerServer server = PartnerServer.start(untrustedLocalhostKeyManagerFactory, trustManagerFactory)) {

            assertThatThrownBy(() -> cut.verifyTlsConnection("localhost", server.getLocalPort()))
                    .isInstanceOf(PartnerConnectionVerificationService.PartnerTlsConnectionException.class)
                    .hasMessageContaining("unable to find valid certification path");
        }
    }

    @Test
    void verifyTlsConnection_unknownHost() throws Exception {
        try (PartnerServer server = PartnerServer.start(localhostKeyManagerFactory, trustManagerFactory)) {

            assertThatThrownBy(() -> cut.verifyTlsConnection("unknown-host", server.getLocalPort()))
                    .isInstanceOf(PartnerConnectionVerificationService.PartnerTlsConnectionException.class)
                    .hasMessageContaining("Error while establishing TLS connection to unknown-host:"
                            + server.getLocalPort()
                            + ": unknown-host");
        }
    }

    private static class PartnerServer implements AutoCloseable {

        private final Thread thread;
        private final CompletableFuture<Integer> port;

        static PartnerServer start(
                KeyManagerFactory keyManagerFactory, TrustManagerFactory trustManagerFactory) {
            CompletableFuture<Integer> port = new CompletableFuture<>();

            Thread thread = new Thread(
                    () -> {
                        try {
                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(
                                    keyManagerFactory.getKeyManagers(),
                                    trustManagerFactory.getTrustManagers(),
                                    new SecureRandom());

                            // Create the SSLServerSocketFactory
                            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();

                            // create the SSLServerSocket and bind it to a port
                            try (SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory
                                    .createServerSocket()) {
                                sslServerSocket.setUseClientMode(false);
                                sslServerSocket.setNeedClientAuth(true);
                                sslServerSocket.bind(new InetSocketAddress("127.0.0.1", 0));
                                port.complete(sslServerSocket.getLocalPort());
                                SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
                                sslSocket.startHandshake();
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            thread.start();
            return new PartnerServer(thread, port);
        }

        public PartnerServer(Thread thread, CompletableFuture<Integer> port) {
            this.thread = thread;
            this.port = port;
        }

        public int getLocalPort() {
            try {
                return port.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            thread.interrupt();
        }
    }
}
