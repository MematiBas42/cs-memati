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
    if l in jsn and jsn[l]:
        item = jsn[l][0]
        print(f"\n[{l}] -> İçerik Var!")
        print(" -> Başlık Anahtarı:", "object_name" if "object_name" in item else "series_name" if "series_name" in item else "BİLİNMİYOR")
        print(" -> Link Anahtarı:", "used_slug" if "used_slug" in item else "episode_used_slug" if "episode_used_slug" in item else "BİLİNMİYOR")
        print(" -> Afiş Anahtarı:", "object_poster_url" if "object_poster_url" in item else "series_poster_url" if "series_poster_url" in item else "BİLİNMİYOR")
    else:
        print(f"\n[{l}] -> BULUNAMADI VEYA BOŞ!")
