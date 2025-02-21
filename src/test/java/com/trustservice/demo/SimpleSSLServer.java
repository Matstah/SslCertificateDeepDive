package com.trustservice.demo;

import org.springframework.util.ResourceUtils;

import javax.net.ssl.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.SocketException;
import java.security.KeyStore;

public class SimpleSSLServer {
    private static final int PORT = 8443;
    private static final String KEYSTORE_NAME = "ubs-local-keystore.p12";
    private static final String KEYSTORE_PASSWORD = "password123";

    private ServerSocket serverSocket;
    private volatile boolean running = true; // Flag to stop the server

    public void start() {
        new Thread(() -> {
            try {
                SSLContext sslContext = createSSLContext();
                SSLServerSocketFactory serverSocketFactory = sslContext.getServerSocketFactory();
                serverSocket = serverSocketFactory.createServerSocket(PORT);

                System.out.println("✅ SSL Server started on port " + PORT);
                System.out.println("🔒 Waiting for client connections...");

                while (running) {
                    try {
                        SSLSocket socket = (SSLSocket) serverSocket.accept();
                        handleClient(socket);
                    } catch (SocketException e) {
                        if (!running) {
                            System.out.println("🛑 Server stopped.");
                            break;
                        }
                    } catch (IOException e) {
                        System.err.println("❌ Error handling client: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(SSLSocket socket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))
        ) {
            System.out.println("🎉 Client connected: " + socket.getInetAddress());

            String clientMessage = reader.readLine();
            System.out.println("📩 Received: " + clientMessage);

            writer.write("Hello, SSL Client!\n");
            writer.flush();
            System.out.println("✅ Response sent.");
        } catch (IOException e) {
            System.err.println("❌ Error handling client: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("❌ Error closing server: " + e.getMessage());
        }
    }

    private static SSLContext createSSLContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keyStoreStream = new FileInputStream(ResourceUtils.getFile("classpath:" + KEYSTORE_NAME))) {
            keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

}
