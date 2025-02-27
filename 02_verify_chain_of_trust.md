# Verify chain of trust

If you want to use your own CAs and certificates, replace the ones in the java project.

You can do this by executing the following command outside of the docker shell:

```bash
sudo cp apps/ubs-local/ubs-local-keystore.p12 src/main/resources/
group="$(groups | cut -d' ' -f1)"
sudo chown "${USER}:${group}" src/main/resources/ubs-local-keystore.p12
```

## SpringBoot endpoint

Run the trustservice springboot demo with `mvn spring-boot:run` and go to `http://localhost:8443`.

You should get a warning that the endpoint needs TLS. So use https instead: `https://localhost:8443`
or `https://127.0.0.1:8443/`. Now the browser should give you a warning that the certificate is not
trusted. This is because the root certificate we self-signed is not in the truststore of the
browser. Accept the risk and continue. You now see the response of our endpoint. Check out the
application.properties and the SSLController files to see how we added the Keystore with the
certificates (or chain of trust) to SpringBoot. In the Browser, inspect the certificates. Do you see
all our Certificate Authorities?

```bash
openssl s_client -connect localhost:8443 -showcerts
```

In order to trust the certificate, either add it to the trust of your browser by going into the
settings, and searching for "manage certificates" within the settings. You can then import the CA
under `./MyCAs/root/certs/ca.crt.pem`. You can also add it to your system trust to ensure the
certificate is trusted by all applications.

## Java Sockets

The [`SSLCertificateTest.java`](./src/main/test/java/com.trustservice.demo/SSLCertificateTest.java) test showcases
- how to use self-created certificates for mocked hosts locally.
- how SSL Sockets are setup with Java.
- how you could verify a server is connected by opening a socket to it, doing a handshake and verifying the host. 
This can be useful on our projects to verify all Gateways, Firewalls or whatever are correctly configured. 
It avoids using a health endpoint or a Ping call that might be blocked to the outside world.

Run the test either through your IDE like Intellij, or
```bash
mvn test
```

Tasks:
- Try to understand what the test does!
- What is the difference between the Keystore and the Truststore?
- Do you know what the Handshake does?
- Why does the test pass without the host verification? 
- Activate host verification. Why does the test fail?
- Replace the keystore and truststore with yours. The test should pass. Do you know why?
- You can add a DNS mapping to your machine such that ubs-local.ch gets mapped to your localhost IP. Can you make the host verification pass?
- Can you make a similar test that tries to setup a socket with google.com and verify the trust?
