#!/usr/bin/bash

dir="$( cd "$( dirname "$0" )" &> /dev/null && pwd )"
cas_dir="$dir/CAs"

cas_dir_names=($(find $cas_dir -mindepth 1 -maxdepth 1 -type d -print))

# Use the */ wildcard to match directories in the directory
# cas_dir_paths=($cas_dir/*(/))
 cas_count=${#cas_dir_names[@]}

if [[ $cas_count -eq 0 ]]; then
    echo "No certificate authority found. Exiting script."
    exit 1
fi

# Extract the last segment (directory name) using parameter expansion
# cas_dir_names=(${cas_dir_paths:t})


# Print the elements of the array
echo "Following CAs found:"
for ca_directory_name in "${cas_dir_names[@]}"; do
  echo "  ${ca_directory_name:t}"
done

read -p "Type a name of the CA: " ca_name

CURRENT_CA_PATH="$cas_dir/$ca_name"

echo "The CA '$ca_name' will be used to issue the server certificate"

echo "Application name will be used as common name and host name."

read -p "Enter application name: " appname

dir="$( cd "$( dirname "$0" )" &> /dev/null && pwd )/apps/$appname"

if [ -d "$dir" ]; then
  echo "Directory $dir already exists"
  exit 1
fi

mkdir -p "$dir"
cd "$dir"


# Generate server certificate for TLS

cat <<- EOF >> req.cnf
[ req ]
distinguished_name = req_distinguished_name
req_extensions      = extensions

[ req_distinguished_name ]
commonName          = Common Name
commonName_default  = $appname
countryName         = Country Name (2 letter code)
countryName_default = CH
countryName_min     = 2
countryName_max     = 2
stateOrProvinceName         = State or Province
stateOrProvinceName_default = Switzerland
localityName                  = Locality Name (eg, city)
localityName_default          = Zurich
organizationName         = Organization Name
organizationName_default = Example Org
organizationalUnitName          = Organizational Unit Name (eg, section)
organizationalUnitName_default  = deepdive

[ extensions ]
subjectAltName = @alt_names

[ alt_names ]
DNS.0 = $appname
#DNS.1 = Whatever else here
EOF

openssl req \
  -new \
  -newkey ec:<(openssl ecparam -name prime256v1) \
  -keyout $appname.key.pem \
  -noenc \
  -out $appname.csr.pem \
  -config req.cnf

openssl ca -batch \
  -config $CURRENT_CA_PATH/intermediate/openssl_intermediate.cnf \
  -extensions server_cert \
  -days 3650 \
  -notext \
  -md sha384 \
  -in $appname.csr.pem \
  -out $appname.crt.pem \
  -passin pass:password

cat "$appname.crt.pem" "$CURRENT_CA_PATH/intermediate/certs/chain.crt.pem" > "$appname.chain.crt.pem"

openssl pkcs12 -export \
  -inkey "$appname.key.pem" \
  -in "$appname.chain.crt.pem" \
  -out "$appname-keystore.p12" \
  -passout pass:password


echo
echo "Content of created keystore.p12:"
echo

keytool -list -v -keystore "$appname-keystore.p12" -storetype PKCS12 -storepass password