from curl_cffi import requests
from bs4 import BeautifulSoup
import re

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"
try:
    r = requests.get(url, impersonate="chrome110")
    html = r.text
    
    # Let's search for "player" or "iframe" or "vidmoly" or "video" in raw html
    print("Raw HTML length:", len(html))
    
    matches = re.findall(r'<iframe[^>]+src=["\']([^"\']+)["\']', html, re.IGNORECASE)
    print("Found iframes:", matches)
    
    links = re.findall(r'<a[^>]+href=["\']([^"\']+)["\']', html, re.IGNORECASE)
    player_links = [l for l in links if 'player' in l or 'video' in l]
    print("Found player links:", player_links)
    
    # Try to find episodes
    episodes = re.findall(r'<a[^>]+href=["\']([^"\']+sezon[^"\']+)["\']', html, re.IGNORECASE)
    print("Found episodes:", list(set(episodes))[:5])
    
    # Check title
    titles = re.findall(r'<title>(.*?)</title>', html, re.IGNORECASE)
    print("Title:", titles)

except Exception as e:
    print("Error:", e)
