from curl_cffi import requests
import re
import json

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"
r = requests.get(url, impersonate="chrome110")
html = r.text

next_match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', html, re.DOTALL)
if next_match:
    data = json.loads(next_match.group(1))
    
    # props -> pageProps içindedir muhtemelen
    pageProps = data.get("props", {}).get("pageProps", {})
    print("Keys in pageProps:", list(pageProps.keys()))
    
    # Bakalım dizilerle ilgili neler var
    if "episode" in pageProps:
        ep = pageProps["episode"]
        print("Episode keys:", list(ep.keys()))
        print("Episode source urls:", ep.get("videoUrls") or ep.get("sources") or ep.get("links") or "Bulunamadı, içindeki verileri arayalım...")
        
    if "season" in pageProps or "episodes" in pageProps or "series" in pageProps:
        print("Found series/episodes structures.")
        
    # Genel bir search
    json_str = json.dumps(pageProps)
    matches = re.findall(r'https?://[^\"]+(?:player|embed|vidmoly|closeload)[^\"]+', json_str)
    print("Found potential video links in JSON:", list(set(matches)))

