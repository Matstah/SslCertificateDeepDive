package com.trustservice.demo;

import javax.net.ssl.*;

import org.springframework.stereotype.Component;

import java.io.*;
import java.security.KeyStore;

//@Component
public class SimpleSSLServer {
    private static final int PORT = 8443;
    private static final String KEYSTORE_PATH = "src/main/resources/keystore.jks";
    private static final String KEYSTORE_PASSWORD = "password123"; // Change to KeystorePassword

    public static void main(String[] args) {
        try {
            SSLContext sslContext = createSSLContext();
            SSLServerSocketFactory serverSocketFactory = sslContext.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(PORT);

            System.out.println("✅ SSL Server started on port " + PORT);
            System.out.println("🔒 Waiting for client connections...");

            while (true) {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    System.out.println("🎉 Client connected: " + socket.getInetAddress());

                    // Setup I/O streams
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                    // Read client message
                    String clientMessage = reader.readLine();
                    System.out.println("📩 Received: " + clientMessage);

                    // Respond to the client
                    writer.write("Hello from SSL Server!\n");
                    writer.flush();

                    System.out.println("✅ Response sent.");
                } catch (IOException e) {
                    System.err.println("❌ Error handling client: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLContext createSSLContext() throws Exception {
        // Load the server's keystore (contains private key + certificate)
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream keyStoreStream = new FileInputStream(KEYSTORE_PATH)) {
            keyStore.load(keyStoreStream, KEYSTORE_PASSWORD.toCharArray());
        }

        // Initialize KeyManagerFactory with the keystore
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        // Set up SSLContext with KeyManagers (for server authentication)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }   
}
