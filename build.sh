#!/bin/bash
# 无 Gradle 打包（需 JDK 8+ 与 Android SDK）
# 用法：ANDROID=/path/to/android-sdk ./build.sh
set -e
ANDROID=${ANDROID:-$ANDROID_HOME}
BT=$ANDROID/build-tools/34.0.0
AJAR=$ANDROID/platforms/android-34/android.jar
NAME=${NAME:-guandan}
MAIN=app/src/main

rm -rf obj dexout gen && mkdir -p obj dexout gen

$BT/aapt2 compile --dir $MAIN/res -o res.zip
$BT/aapt2 link -o base.apk -I $AJAR -A $MAIN/assets --manifest $MAIN/AndroidManifest.xml \
  --java gen --min-sdk-version 21 --target-sdk-version 34 res.zip

find $MAIN/java gen -name "*.java" > files.txt
javac -source 8 -target 8 -bootclasspath $AJAR -d obj @files.txt

$BT/d8 --release --min-api 21 --lib $AJAR --output dexout $(find obj -name "*.class")
cp base.apk unsigned.apk
(cd dexout && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f 4 unsigned.apk aligned.apk
$BT/apksigner sign --ks "${KS:-release.keystore}" --ks-pass "${KS_PASS:-pass:android}" \
  --key-pass "${KEY_PASS:-pass:android}" --out "$NAME.apk" aligned.apk
echo "产物：$NAME.apk"
