from curl_cffi import requests
from bs4 import BeautifulSoup
import re

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"
r = requests.get(url, impersonate="chrome110")
html = r.text

print("--- JAVASCRIPT / JSON DATA ---")
scripts = re.findall(r'<script[^>]*>(.*?)</script>', html, re.DOTALL)
for i, s in enumerate(scripts):
    if "player" in s.lower() or "video" in s.lower() or "source" in s.lower() or "iframe" in s.lower() or "NEXT_DATA" in s.lower() or "NUXT" in s.lower():
        print(f"Script #{i} length: {len(s)}")
        # Print a snippet of it
        print(s[:300] + "...\n")
