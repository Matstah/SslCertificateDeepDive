# Deep Dive into SSL certificates

How to create a signed certificate chain. This article is my effort to do a deep dive into the lifecycle of the certificate, including:

- Create a Root Certificate Authority (CA) and self-signing its certificate.
- Create an Intermediate CA and signing its Certificate by the Root CA.
- Create a client & server Certificate signed by the Intermediate CA.
- Using OpenSSL
- Add a lot of explanation through the way.

Why should you care?

- This thing appears in any project where you use HTTPS.
- Securing an intranet website
- Issuing certificates to clients to allow them to authenticate to a server
- Create test setup's where you have full control

Mutual TLS authentication, also known as client-server authentication, is a robust security mechanism that requires both the client and the server to present valid digital certificates before establishing a secure connection. This added layer of authentication helps ensure that only trusted entities can access the protected resources.

## Create a Root Certificate Authority

1. Make a directory for the Root CA to live in.
2. Create a root CA configuration file
3. Generate a private key and store it savely
4. Create a Root Certificate
5. Self-Sign the Root Certificate with the root's own private key.

Root Certificates are the center of Public Key Infrastructure (PKI). They are sacred and at the center of the trust model. Every device comes with a so-called root store. A root store is a collection of pre-downloaded root certificates, along with their public keys, that reside on the device. Devices use either the root store built into its operating system or a third-party root store via an application like a web browser. The browsers will automatically trust any certificate signed by a trusted Root. In MAC go to -> KeyChain Access.

### 1. Prepare the directory

Choose a directory to store all keys and certificates.

```
mkdir -p "$dir"
cd "$dir"
mkdir root
cd root
```

Create the directory structure. The index.txt and serial files act as a flat file database to keep track of signed certificates.

```
mkdir private certs crl csr
touch index.txt
touch index.txt.attr
echo 1000 > serial
echo 1000 > crlnumber
```

- _index.txt_ and _serial_ files act as a flat file database to keep track of signed certificates.
- _crlnumber_ is used to keep track of certificate revocation lists.

### 2. Create a root CA configuration file

A Certificate Authority (CA) configuration file in OpenSSL is crucial because it defines the parameters and rules that guide how the CA operates, manages certificates, and ensures consistency across issued certificates. I.e. to only sign certificate from servers in Switzerland with a TTL of max one year. If it is not specified, OpenSSL will take the default settings.

The section in the openssl_root.cnf file contains the variables OpenSSL will use for the root CA. If you're using alternate directory names, update the file accordingly. Note the long values for default days (10000 -> 27 years) as we don't care about renewing the root certificate anytime soon. This is aso done in real life - You generate a private key somewhere in a bunker deep underground, rip out the network card and put glue in the ethernet port.

<br>
The [ ca ] section is mandatory for the Root CA certificate creation. Here we tell OpenSSL to use the options from the [ CA_default ] section.

```
[ ca ]
default_ca = CA_default
```

The [ CA_default ] section contains a range of defaults. Make sure you declare the directory you chose earlier (/root/ca).

```
[ CA_default ]
# Directory and file locations.
dir               = $dir/root
certs             = \$dir/certs
crl_dir           = \$dir/crl
new_certs_dir     = \$dir/certs
database          = \$dir/index.txt
serial            = \$dir/serial
RANDFILE          = \$dir/private/.rand
```

The root key and root certificate.

```
private_key       = \$dir/private/ca.key.pem
certificate       = \$dir/certs/ca.crt.pem
```

Config for certificate revocation lists. I.e. Cloudflare's private key is leaked, and the root CA needs to tell everyone to revocate trusting all Cloudflare signed certificates.

```
crlnumber         = \$dir/crlnumber
crl               = \$dir/crl/ca.crl.pem
crl_extensions    = crl_ext
default_crl_days  = 10000
```

SHA-1 is deprecated, so use SHA-2 or SHA-3 instead.

```
default_md        = sha384

name_opt          = ca_default
cert_opt          = ca_default
default_days      = 10000
preserve          = no
policy            = policy_strict
```

We’ll apply policy_strict for all root CA signatures, as the root CA is only being used to create intermediate CAs.

```
[ policy_strict ]
# The root CA should only sign intermediate certificates that match.
countryName             = match
stateOrProvinceName     = match
organizationName        = match
organizationalUnitName  = optional
commonName              = supplied
emailAddress            = optional
```

Options from the [ req ] section are applied when creating certificates or certificate signing requests.

```
[ req ]
default_bits        = 2048
distinguished_name  = req_distinguished_name
string_mask         = utf8only
default_md          = sha256
```

The [ req_distinguished_name ] section declares the information normally required in a certificate signing request. You can optionally specify some defaults. See <https://en.wikipedia.org/wiki/Certificate_signing_request>

```
[ req_distinguished_name ]
countryName                     = Country Name (2 letter code)
stateOrProvinceName             = State or Province Name
localityName                    = Locality Name
0.organizationName              = Organization Name
organizationalUnitName          = Organizational Unit Name
commonName                      = Common Name
emailAddress                    = Email Address
```

The next few sections are extensions that can be applied when signing certificates. For example, passing the -extensions v3_ca command-line argument will apply the options set in [ v3_ca ].

We’ll apply the v3_ca extension when we create the root certificate.

```
[ v3_ca ]
# Extensions for a typical root CA
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true
keyUsage = critical, digitalSignature, cRLSign, keyCertSign
```

We’ll apply the v3_ca_intermediate extension when we create the intermediate certificate. pathlen:0 ensures that there can be no further certificate authorities below the intermediate CA.

```
[ v3_intermediate_ca ]
# Extensions for a typical intermediate CA
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, digitalSignature, cRLSign, keyCertSign
crlDistributionPoints = @crl_info
authorityInfoAccess = @ocsp_info
```

We’ll apply the ocsp extension when signing the [Online Certificate Status Protocol (OCSP)](https://en.wikipedia.org/wiki/Online_Certificate_Status_Protocol) certificate. OCSP was created as an alternative to certificate revocation lists (CRLs). It’s recommended to use OCSP instead where possible, though realistically you will tend to only need OCSP for website certificates.

```
[ ocsp ]
basicConstraints = CA:FALSE
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = critical, OCSPSigning

[ ocsp_info ]
caIssuers;URI.0 = http://$ca_name/root/certificate
OCSP;URI.0 = http://$ca_name/root/ocsp
```

The crl_ext extension is automatically applied when creating certificate revocation lists.

```
[ crl_ext ]
authorityKeyIdentifier=keyid:always

[ crl_info ]
URI.0 = http://$ca_name/root/crl
```

### 3. Generate a private key and store it savely

Generate a private key and store it in private/ca.key.pem encrypted.

```
openssl ecparam -genkey -name secp384r1 | openssl ec -aes256 -out private/ca.key.pem -passout pass:password
```

Note: OpenSSL can generate a new private key using various cryptographic algorithms, such as RSA or ECC (Elliptic Curve Cryptography). Deep dive into them in case you setup an actual authority.

### 4. Create a Self-Signed Root Certificate

Next, we create a self-signed certificate containing based on our config and using the private key. The certificate will contain the public key, some information about the Root CA, and the signature.

```
openssl req -new -x509 -config openssl_root.cnf -sha384 -extensions v3_ca -key private/ca.key.pem -passin pass:password -out certs/ca.crt.pem -subj "/CN=$ca_name Root CA/C=CH/ST=Zurich/L=Zürich/O=Stahli Team/OU=msh" -days 10000
```

- _openssl req_ invokes the OpenSSL request processing tool, used to generate certificate signing requests (CSRs), self-signed certificates, or certificates.
- _-x509_ specifies that the output should be a self-signed certificate instead of a certificate signing request (CSR).
- _-sha384_ specifies the hashing algorithm (SHA-384) to use for signing the certificate.

Basically, the certificate is hashed and the hash encrypted with the private key. You can then hash the certificate yourself, decrypt the signature using the public key and compare both hashed. So every certificate contains some CA information, the public key and the signature.

## Create an Intermediate Certificate Authority

Root certificates are invaluable and few. If they have to certify all the businesses in the world, they will be overwhelmed in no time. Root CAs have to do a lot of scrutinies and put a lot of effort to keep their private keys unobtainable.

That is the reason Root Certificates delegate the responsibility to other CAs like CloudFlare to certify common business certificates.

This type of responsibility is called certificate chaining resulting in “Certification Paths”. This process can be repeated several times, where an intermediate root signs another intermediate and finally signs an end entity certificate.

- The Intermediate CA will create a Certificate Signing Request and sends it to the Root CA to be signed. This is basically the only difference. So no self-signing.

### 1. Prepare the directory & create a CA config file

```
cd $dir
mkdir intermediate
cd intermediate
mkdir certs crl csr private
touch index.txt
touch index.txt.attr
echo 2000 > serial
echo 2000 > crlnumber
```

Create an openssl_intermediate.cnf with the respective paths.

### 2. Generate new private key & a certificate signing request (CSR)

This command is used to generate a new private key and a certificate signing request (CSR) for an Intermediate Certificate Authority (Intermediate CA)

```
openssl req -new \
  -newkey ec:<(openssl ecparam -name $curve) \
  -keyout intermediate/private/int.key.pem \
  -passout pass:password \
  -out intermediate/csr/int.csr \
  -subj "/CN=$ca_name Intermediate CA/C=SK/ST=Slovakia/L=Bratislava/O=Intapp Team/OU=pki"
```

- The new private key is stored at intermediate/private/int.key.pem and password protected with 'password'.

Check that the details of the intermediate certificate are correct.

```
openssl x509 -noout -text -in intermediate/certs/int.crt.pem
```

### 3. Root signs the Intermediate CA certificate.

To create an intermediate certificate, use the root CA with the v3_intermediate_ca extension to sign the intermediate CSR. The intermediate certificate should be valid for a shorter period than the root certificate. Ten years would be reasonable.

```
openssl ca -batch -config root/openssl_root.cnf -extensions v3_intermediate_ca -days 3650 \
  -md sha384 -in intermediate/csr/int.csr -out intermediate/certs/int.crt.pem \
  -passin pass:password \
  -notext
```

The Root index.txt file is where the OpenSSL ca tool stores the certificate database. Do not delete or edit this file by hand. It should now contain a line that refers to the intermediate certificate.

Verify the intermediate certificate by checking that details of the intermediate certificate are correct.

```
openssl x509 -noout -text -in intermediate/certs/int.crt.pem
```

Verify the intermediate certificate against the root certificate. An OK indicates that the chain of trust is intact.

```
openssl verify -CAfile root/certs/ca.crt.pem intermediate/certs/int.crt.pem
```

Note: To verify your entire chain in one command:

```
openssl verify -CAfile RootCert.pem -untrusted Intermediate.pem UserCert.pem
```

### 4. Create the certificate chain file

To create the CA certificate chain, concatenate the intermediate and root certificates together. We will use this file later to verify certificates signed by the intermediate CA.

```
cat intermediate/certs/int.crt.pem root/certs/ca.crt.pem > intermediate/certs/chain.crt.pem

chmod 444 intermediate/certs/chain.crt.pem
```

Note: Our certificate chain file must include the root certificate because no client application knows about it yet. A better option, particularly if you’re administrating an intranet, is to install your root certificate on every client that needs to connect. In that case, the chain file need only contain your intermediate certificate.

## Sign server and client certificates

It is quite similar to the intermediate case. We create a Certificate Signature Request for our domain, send it to our CA and let it be signed. The domain owner keeps the private key.

Run `server-cert.sh` and tell it to use the created CA.
It will generate a Keystore file containing the full chain.

But what and how is put into our Java application?

It seems that each certificate also contains a fingerpring. Fingerprints are merely hash representations of certificates and cannot participate in cryptographic operations like signing, encryption, or verification. The fingerprint is basically a hash containing all certificate information and the signature (so also public key). It is an easy and fast way to check if things match, because if the fingerprint match, everything else must also match. But how is the rest done?

Why can our demo work with only the fingerprints instead of the certificate.

Seems like client contains private key, localhost and truststores fingerprints.

Things to Ensure:

- Truststore Contains Root CA:
  The root certificate (Certificate[3]) must be in the truststore (truststore.p12) of the client.
- Password Protection: The keystore should have a strong password.
- Hostname Verification: The client must verify CN=localhost or SAN localhost to avoid certificate mismatch errors.

---> -v makes a big difference! The -v flag in the keytool -list command stands for "verbose." It provides a detailed, expanded output about the contents of the keystore.

```
keytool -list -keystore localhost-keystore.p12 -storepass password
```

vs.

```
keytool -list -v -keystore localhost-keystore.p12 -storepass password
```

# learnings & definitions

## .pem files

A .pem file is a file format used to store and transmit cryptographic keys, certificates, and other data in a Base64-encoded format. It is commonly used in public key infrastructure (PKI) systems and is compatible with many cryptographic libraries, including OpenSSL.

.pem is the general file extension, but other extensions like .crt, .cer, .key, or .csr may also use the same PEM format.

If you have a .pem file named certificate.pem:

`cat certificate.pem`

To view detailed information of a PEM encoded certificate:

`openssl x509 -in certificate.pem -text -noout`

## What is a signature?

A signature of a certificate is a piece of data inside the certificate which is the only thing that is used to validate the integrity and validity of the certificate.
`Signature = encrypted with private key ( The checksum of the certificate itself in the mentioned algorithm)`
The verifier needs to decrypt the signature by the provided public key in the certificate to get the checksum. Then the verifier will calculate the checksum of the cert itself. These 2 checksums must match to ascertain the integrity and validity of the user sending the certificate.

## The certificate authority signs the certificate

To sign, the certificate authority

1. Calculates the checksum of Tom’s certificate(in Pic 1) using some algorithm.
2. Encrypts the checksum using its (The certificate authority’s) private key
3. Adds the encrypted checksum in Tom’s certificate as a signature.
4. Adds its own information/metadata(The certificate authority’s) in the certificate

## Root Certificate Authority & Intermediate Certificate

(Root CAs generally have Root at the end of their name to make them obvious).

Note, here the issuer and the certifier are the same, so the Root Certificate Authority does not need any otherentity to certify themselves. The self sign their certificate(Uses own private key to sign) to make the signatur(Highlighted purple)
This is another way to identify whether it is a ROOT or Intermediate certificate. See the issue and subject section, if they are the same then it is a ROOT otherwise Intermediate.

Every device comes with a so-called root store. A root store is a collection of pre-downloaded root certificates, along with their public keys, that reside on the device. Devices use either the root store built into its operating system or a third-party root store via an application like a web browser. The root stores are part of root programs, like the ones from Microsoft, Apple, Google, and Mozilla.

While the intermediate certificates have a validity of 1–3 years, root certificates have a long lifespan. Notice the Baltimore Cybertrust Root is valid for 25 years.

Root certificates are invaluable and few. If they have to certify all the businesses in the world, they will be overwhelmed in no time. Root CAs have to do a lot of scrutinies and put a lot of effort to keep their private keys unobtainable. That is the reason Root Certificates delegate the responsibility to other CAs like CloudFlare to certify common business certificates. This type of responsibility is called certificate chaining. In RFC5280 this is called “Certification Path”. This process can be repeated several times, where an intermediate root signs another intermediate and finally signs an end entity certificate

## java

In Java we have 2 files:

keystore.jks : This file contains the server certificate including the private key of the server(like Tom’s website server). The Keystore file is protected with a password, initially changeit. This file can be externally provided in the JVM arguments using -Djavax.net.ssl.trustStore= <Path>

cacerts ($JAVA_HOME/lib/security/cacerts): This file is the one containing all the root certificates and their public keys.
