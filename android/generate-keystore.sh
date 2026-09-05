#!/bin/bash
# Generate release keystore for signing APK
cd "$(dirname "$0")"

if [ ! -f "release.jks" ]; then
    echo "Generating release keystore..."
    keytool -genkey -v -keystore release.jks \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -alias jarebornkey \
        -storepass jarebornkey123 \
        -keypass jarebornkey123 \
        -dname "CN=JA2 Reborn, O=JA2 Reborn, L=Earth, ST=Earth, C=US"
    echo "Keystore generated successfully!"
else
    echo "Keystore already exists at release.jks"
fi
