import hashlib, base64, json, re
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from curl_cffi import requests

key = base64.b64encode(hashlib.sha256(b"!!22xx!!90!!").digest()).decode('utf-8')[:32].encode('utf-8')
iv = bytes([0] * 16)

r = requests.get("https://dizilla.now/", impersonate="chrome110")
match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', r.text, re.DOTALL)
data = json.loads(match.group(1))
secure = data.get("props", {}).get("pageProps", {}).get("secureData", "")
dec = unpad(AES.new(key, AES.MODE_CBC, iv).decrypt(base64.b64decode(secure)), AES.block_size).decode('utf-8')
jsn = json.loads(dec[dec.find('{'):])

print("--- SON EKLENEN BÖLÜMLER TÜM DEĞİŞKENLER ---")
print(list(jsn['getEpisodesOnBrandAll'][0].keys()))

print("\n--- POPÜLER DİZİLER TÜM DEĞİŞKENLER ---")
if 'items' in jsn['allPopularSeries'] and jsn['allPopularSeries']['items']:
    print(list(jsn['allPopularSeries']['items'][0].keys()))
