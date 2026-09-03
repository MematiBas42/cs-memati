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

lists_to_check = ['getEpisodesOnBrandAll', 'getLastSeriesAll', 'getTrendSeries', 'allPopularSeries', 'getEpisodesOnNewSeries', 'getEpisodesOnNewSeason', 'getSeriesByAdvancedSliderWithDetail']

for l in lists_to_check:
    if l in jsn:
        obj = jsn[l]
        print(f"\n--- {l} ---")
        if isinstance(obj, list) and len(obj) > 0:
            print("Gerçek Değişken İsimleri:", list(obj[0].keys())[:10])
        elif isinstance(obj, dict):
            print("Bu bir Liste değil Obje! Anahtarları:", list(obj.keys())[:10])
