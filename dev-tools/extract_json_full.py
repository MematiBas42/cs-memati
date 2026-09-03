from curl_cffi import requests
import re
import json

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"
r = requests.get(url, impersonate="chrome110")
html = r.text

print("Looking for video players in HTML attributes or script tags...")

# Look for data-url, data-src, or any iframe hidden in base64 or similar
matches = re.findall(r'data-[\w-]+=["\']([^"\']+)["\']', html)
for m in matches:
    if 'player' in m or 'video' in m or 'embed' in m:
        print("Found data attribute:", m)

# Search for JSON states (like window.__NUXT__)
nuxt_match = re.search(r'window\.__NUXT__\s*=\s*(.*?);</script>', html, re.DOTALL)
if nuxt_match:
    print("Found NUXT state, length:", len(nuxt_match.group(1)))
    
next_match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', html, re.DOTALL)
if next_match:
    print("Found NEXT_DATA state, length:", len(next_match.group(1)))
    
# Let's search for "vidmoly", "closeload", "fembed", "dizilla.now/player"
for term in ["vidmoly", "closeload", "fembed", "player", "iframe"]:
    term_matches = re.findall(r'.{0,50}' + term + r'.{0,100}', html, re.IGNORECASE)
    if term_matches:
        print(f"\n--- Found '{term}' in HTML ---")
        for tm in list(set(term_matches))[:5]:
            print(tm)

