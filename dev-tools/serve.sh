#!/bin/bash

echo "Eski kalıntılar temizleniyor..."
rm -rf build/
rm -f Dizilla/build/*.cs3

echo "Derleme başlatılıyor..."
./gradlew make --no-daemon

mkdir -p build/
cp Dizilla/build/Dizilla.cs3 build/ 2>/dev/null || true

IP=$(ip route get 1 | awk '{print $(NF-2);exit}')
# Cache buster için anlık zaman damgası
TIME=$(date +%s)

echo "Localhost repo.json oluşturuluyor..."
cat << JSON > build/local_repo.json
{
  "name": "Local Test Repo",
  "description": "Yerel ag uzerinden aninda test eklentileri",
  "manifestVersion": 1,
  "pluginLists": [
    "http://$IP:8080/plugins.json?t=$TIME"
  ]
}
JSON

CS3_FILE="build/Dizilla.cs3"
if [ -f "$CS3_FILE" ]; then
    FILE_SIZE=$(stat -c%s "$CS3_FILE")
    FILE_HASH=$(sha256sum "$CS3_FILE" | awk '{print $1}')
    
cat << JSON > build/plugins.json
[
  {
    "iconUrl": "https://www.google.com/s2/favicons?domain=dizilla.now&sz=%size%",
    "fileHash": "sha256-$FILE_HASH",
    "apiVersion": 1,
    "repositoryUrl": "http://$IP:8080",
    "fileSize": $FILE_SIZE,
    "status": 1,
    "language": "tr",
    "authors": ["MematiBas42"],
    "tvTypes": ["TvSeries"],
    "version": 1,
    "internalName": "Dizilla",
    "description": "Dizilla - Yabancı dizi izleme platformu eklentisi.",
    "url": "http://$IP:8080/Dizilla.cs3?t=$TIME",
    "name": "Dizilla"
  }
]
JSON
fi

echo ""
echo "------------------------------------------------------"
echo "CloudStream uygulamasına eklenecek Repository URL:"
echo "http://$IP:8080/local_repo.json?t=$TIME"
echo "------------------------------------------------------"
echo "Sunucu başlatıldı! Kapatmak için CTRL+C yapın."
cd build && python3 -m http.server 8080
