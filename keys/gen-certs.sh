#!/usr/bin/env bash

# Generate CA certificate
keytool -genkeypair -v \
  -alias pharmrusca \
  -dname "CN=pharmrusCA, OU=PHARMRUS, O=PHARMRUS, L=Moscow, ST=Moscow, C=RU" \
  -keystore pharmrusca.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 4096 \
  -ext KeyUsage:critical="keyCertSign" \
  -ext BasicConstraints:critical="ca:true" \
  -validity 9999

# Export CA certificate
keytool -export -v \
  -alias pharmrusca \
  -file pharmrusca.crt \
  -keypass:env PW \
  -storepass:env PW \
  -keystore pharmrusca.jks \
  -rfc

# Generate host certificate
keytool -genkeypair -v \
  -alias pharmrus.ru \
  -dname "CN=pharmrus24.ru, OU=PHARMRUS, O=PHARMRUS, L=Moscow, ST=Moscow, C=RU" \
  -keystore pharmrus.ru.jks \
  -keypass:env PW \
  -storepass:env PW \
  -keyalg RSA \
  -keysize 2048 \
  -validity 385

# Create a certificate signing request for pharmrus.ru
keytool -certreq -v \
  -alias pharmrus.ru \
  -keypass:env PW \
  -storepass:env PW \
  -keystore pharmrus.ru.jks \
  -file pharmrus.ru.csr

# Tell pharmrusca to sign the pharmrus.ru certificate. Note the extension is on the request, not the
# original certificate.
# Technically, keyUsage should be digitalSignature for DHE or ECDHE, keyEncipherment for RSA.
keytool -gencert -v \
  -alias pharmrusca \
  -keypass:env PW \
  -storepass:env PW \
  -keystore pharmrusca.jks \
  -infile pharmrus.ru.csr \
  -outfile pharmrus.ru.crt \
  -ext KeyUsage:critical="digitalSignature,keyEncipherment" \
  -ext EKU="serverAuth" \
  -ext SAN="DNS:pharmrus.ru" \
  -rfc

# Tell pharmrus.ru.jks it can trust pharmrusca as a signer.
keytool -import -v \
  -alias pharmrusca \
  -file pharmrusca.crt \
  -keystore pharmrus.ru.jks \
  -storetype JKS \
  -storepass:env PW << EOF
yes
EOF

# Import the signed certificate back into pharmrus.ru.jks 
keytool -import -v \
  -alias pharmrus.ru \
  -file pharmrus.ru.crt \
  -keystore pharmrus.ru.jks \
  -storetype JKS \
  -storepass:env PW

# List out the contents of pharmrus.ru.jks just to confirm it.  
# If you are using Play as a TLS termination point, this is the key store you should present as the server.
keytool -list -v \
  -keystore pharmrus.ru.jks \
  -storepass:env PW