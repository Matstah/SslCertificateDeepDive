#!/usr/bin/bash

echo "Let's setup a Certificate Authority"
read -p "Enter CA name: " ca_name

# Set dir to the path of a directory based on the location of the current script and a specified subdirectory (CAs/$ca_name).
# In this case, it should create a "/CAs/$ca_name" folder next to this script. /home/user/scripts/CAs/my_ca
# $0 is the name of the current process
# $(..) Command substitution. Returns the output of the command inside
# cd "$( dirname "$0" )": This changes the current directory to the directory containing the script.
# &> /dev/null: Redirects both standard output (stdout) and standard error (stderr) to /dev/null, effectively silencing any output or error messages from the cd command.
dir="$( cd "$( dirname "$0" )" &> /dev/null && pwd )/CAs/$ca_name"

if [ -d "$dir" ]; then
  echo "Directory $dir already exists"
  exit 1
fi

# create a folder for the root CA
mkdir -p "$dir"
cd "$dir"
mkdir root
cd root

# Initializes essential files for OpenSSL
# crlnumber is used to keep track of certificate revocation lists.
mkdir private certs crl csr
touch index.txt
touch index.txt.attr
echo 1000 > serial
echo 1000 > crlnumber

curve="secp384r1"
leafCurve="prime256v1"

# Create openssl_root config file.
# The content between cat << EOF and EOF is treated as input to the cat command.
# The > operator redirects the output to a file.
# crl: certificate revocation list.
cat << EOF > openssl_root.cnf
[ ca ]
default_ca = CA_default

[ CA_default ]
# Directory and file locations.
dir               = $dir/root
certs             = \$dir/certs
crl_dir           = \$dir/crl
new_certs_dir     = \$dir/certs
database          = \$dir/index.txt
serial            = \$dir/serial
RANDFILE          = \$dir/private/.rand

# The root key and root certificate.
private_key       = \$dir/private/ca.key.pem
certificate       = \$dir/certs/ca.crt.pem

# For certificate revocation lists.
crlnumber         = \$dir/crlnumber
crl               = \$dir/crl/ca.crl.pem
crl_extensions    = crl_ext
default_crl_days  = 10000

default_md        = sha384

name_opt          = ca_default
cert_opt          = ca_default
default_days      = 10000
preserve          = no
policy            = policy_strict

[ policy_strict ]
# Allow the intermediate CA to sign a more diverse range of certificates.
countryName             = match
stateOrProvinceName     = match
organizationName        = match
organizationalUnitName  = optional
commonName              = supplied
emailAddress            = optional

[ req ]
default_bits        = 2048
distinguished_name  = req_distinguished_name
string_mask         = utf8only
default_md          = sha256
x509_extensions     = v3_ca

[ req_distinguished_name ]
countryName                     = Country Name (2 letter code)
stateOrProvinceName             = State or Province Name
localityName                    = Locality Name
0.organizationName              = Organization Name
organizationalUnitName          = Organizational Unit Name
commonName                      = Common Name
emailAddress                    = Email Address

[ v3_ca ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true
keyUsage = critical, digitalSignature, cRLSign, keyCertSign

[ v3_intermediate_ca ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, digitalSignature, cRLSign, keyCertSign
crlDistributionPoints = @crl_info
authorityInfoAccess = @ocsp_info

[ ocsp ]
basicConstraints = CA:FALSE
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = critical, OCSPSigning

[ crl_ext ]
authorityKeyIdentifier=keyid:always

[ crl_info ]
URI.0 = http://$ca_name/root/crl

[ ocsp_info ]
caIssuers;URI.0 = http://$ca_name/root/certificate
OCSP;URI.0 = http://$ca_name/root/ocsp

EOF

echo
echo "Generate Root CA certificate"
echo

# Generate a private key and store it savely in private/ca.key.pem encrypted with the password 'password'.
# OpenSSL can generate a new private key using various cryptographic algorithms, such as RSA or ECC (Elliptic Curve Cryptography).
openssl ecparam -genkey -name $curve | openssl ec -aes256 -out private/ca.key.pem -passout pass:password
chmod 400 private/ca.key.pem
# -req: generate a signing request. We will be asked some questions. At the end, a .crt will be created.
# In this case, self-signing the certificate.
# The ca.crt.pem file is not the certificate that I can use for my Server. 
# It is the digital form (A standard way) of submitting a certificate signing request.
# C=CH/ST=Switzerland/L=Zurich
openssl req -config openssl_root.cnf -new -x509 -sha384 -extensions v3_ca \
  -key private/ca.key.pem -passin pass:password -out certs/ca.crt.pem \
  -subj "/CN=$ca_name Root CA/C=CH/ST=Switzerland/L=Zurich/O=Intapp Team/OU=pki" \
  -days 10000

# print the certificate
openssl x509 -noout -text -in certs/ca.crt.pem

echo
echo "Generate Root CA certificate - FINISHED"
echo 

# Generate CRL for root
echo
echo "Generate CRL for Root CA"
echo

openssl ca -config openssl_root.cnf \
  -gencrl -out crl/ca.crl.pem \
  -passin pass:password

echo
echo "Generate CRL for Root CA - FINISHED"
echo

# Generate certificate for OCSP for checking certificates issued by the root
echo
echo "Generate OCSP CA Certificate for the Root CA"
echo

openssl req \
  -new \
  -newkey ec:<(openssl ecparam -name $curve) \
  -keyout private/ocsp-root.key.pem \
  -passout pass:password \
  -out csr/ocsp-root.csr.pem \
  -subj "/CN=$ca_name OCSP for root/C=CH/ST=Switzerland/L=Zurich/O=Intapp Team/OU=pki" 
openssl ca -batch \
  -config openssl_root.cnf \
  -extensions ocsp \
  -days 10000 \
  -notext \
  -md sha384 \
  -in csr/ocsp-root.csr.pem \
  -out certs/ocsp-root.crt.pem \
  -passin pass:password

echo
echo "Generate OCSP CA Certificate for the Root CA - FINISHED"
echo

cd $dir
mkdir intermediate
cd intermediate
mkdir certs crl csr private
touch index.txt
touch index.txt.attr
echo 2000 > serial
echo 2000 > crlnumber

cat << EOF > openssl_intermediate.cnf
[ ca ]
default_ca = CA_default

[ CA_default ]
dir               = $dir/intermediate
certs             = \$dir/certs
crl_dir           = \$dir/crl
new_certs_dir     = \$dir/certs
database          = \$dir/index.txt
serial            = \$dir/serial
RANDFILE          = \$dir/private/.rand

private_key       = \$dir/private/int.key.pem
certificate       = \$dir/certs/int.crt.pem

unique_subject    = no # Allow creation of several certs with the same subject.
copy_extensions   = copy

# For certificate revocation lists.
crlnumber         = \$dir/crlnumber
crl               = \$dir/crl/int.crl.pem
crl_extensions    = crl_ext
default_crl_days  = 1800

default_md        = sha384

name_opt          = ca_default
cert_opt          = ca_default
default_days      = 3000
preserve          = no
policy            = policy_loose

[ policy_loose ]
# Allow the intermediate CA to sign a more diverse range of certificates. Can be checked by root.
countryName             = optional
stateOrProvinceName     = optional
localityName            = optional
organizationName        = optional
organizationalUnitName  = optional
commonName              = supplied
emailAddress            = optional

[ req ]
default_bits        = 2048
distinguished_name  = req_distinguished_name
string_mask         = utf8only
default_md          = sha256

[ req_distinguished_name ]
countryName                     = Country Name (2 letter code)
stateOrProvinceName             = State or Province Name
localityName                    = Locality Name
0.organizationName              = Organization Name
organizationalUnitName          = Organizational Unit Name
commonName                      = Common Name
emailAddress                    = Email Address

[ crl_ext ]
authorityKeyIdentifier=keyid:always

[ ocsp ]
basicConstraints = CA:FALSE
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid,issuer
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = critical, OCSPSigning

[ crl_info ]
URI.0 = http://$ca_name/intermediate/crl

[ ocsp_info ]
caIssuers;URI.0 = http://$ca_name/intermediate/certificate
OCSP;URI.0 = http://$ca_name/intermediate/ocsp

[ server_cert ]
basicConstraints = CA:FALSE
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
crlDistributionPoints = @crl_info
authorityInfoAccess = @ocsp_info

EOF

cd "$dir"

echo
echo "Generate Intermediate CA certificate"
echo

openssl req -new \
  -newkey ec:<(openssl ecparam -name $curve) \
  -keyout intermediate/private/int.key.pem \
  -passout pass:password \
  -out intermediate/csr/int.csr \
  -subj "/CN=$ca_name Intermediate CA/C=CH/ST=Switzerland/L=Zurich/O=Intapp Team/OU=pki"

openssl ca -batch -config root/openssl_root.cnf -extensions v3_intermediate_ca -days 10000 \
  -md sha384 -in intermediate/csr/int.csr -out intermediate/certs/int.crt.pem \
  -passin pass:password \
  -notext
openssl x509 -noout -text -in intermediate/certs/int.crt.pem

cat intermediate/certs/int.crt.pem root/certs/ca.crt.pem > intermediate/certs/chain.crt.pem

echo
echo "Generate Intermediate CA certificate - FINISHED"
echo
echo
echo "Generate CRL for Intermediate CA"
echo

openssl ca -config intermediate/openssl_intermediate.cnf -gencrl -out intermediate/crl/int.crl.pem -passin pass:password
openssl crl -in intermediate/crl/int.crl.pem -noout -text

echo
echo "Generate CRL for Intermediate CA - FINISHED"
echo

# Generate certificate for OCSP for checking certificates issued by the intermediate

echo
echo "Generate OCSP CA Certificate for the Intermediate CA" 

echo
openssl req \
  -new \
  -newkey ec:<(openssl ecparam -name $curve) \
  -keyout intermediate/private/ocsp-int.key.pem \
  -passout pass:password \
  -out intermediate/csr/ocsp-int.csr.pem \
  -subj "/CN=$ca_name OCSP for int/C=CH/ST=Switzerland/L=Zurich/O=Intapp Team/OU=pki"

openssl ca -batch \
  -config intermediate/openssl_intermediate.cnf \
  -extensions ocsp \
  -days 10000 \
  -notext \
  -md sha384 \
  -in intermediate/csr/ocsp-int.csr.pem \
  -out intermediate/certs/ocsp-int.crt.pem \
  -passin pass:password

echo
echo "Generate OCSP CA Certificate for the Intermediate CA - FINISHED" 
echo

echo "Done"
