from curl_cffi import requests
import re
import json
import base64

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"
r = requests.get(url, impersonate="chrome110")
html = r.text

next_match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', html, re.DOTALL)
if next_match:
    data = json.loads(next_match.group(1))
    pageProps = data.get("props", {}).get("pageProps", {})
    
    secureData = pageProps.get("secureData", "")
    print("secureData is a string, length:", len(secureData))
    if secureData:
        print("First 100 chars:", secureData[:100])
        # Often this is base64 + some custom simple cipher or AES
        # Let's see if we can decode it via base64
        try:
            decoded = base64.b64decode(secureData).decode('utf-8', errors='ignore')
            print("Base64 decoded length:", len(decoded))
            print("Decoded snippet:", decoded[:200])
            
            # If it's valid JSON
            if decoded.startswith("{"):
                parsed = json.loads(decoded)
                print("Decoded JSON keys:", list(parsed.keys()))
        except Exception as e:
            print("Could not base64 decode:", e)

