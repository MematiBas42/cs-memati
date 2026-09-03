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

print("TÜM KÖK ANAHTARLAR:", list(jsn.keys()))

def inspect_list(name):
    if name in jsn and isinstance(jsn[name], list) and len(jsn[name]) > 0:
        print(f"\n--- {name} ({len(jsn[name])} Öğe) ---")
        item = jsn[name][0]
        print("KULLANILABİLİR ANAHTARLAR:", list(item.keys())[:10])
        print("BAŞLIK:", item.get('object_name') or item.get('series_name') or item.get('culture_title') or item.get('original_title'))
        print("BÖLÜM METNİ:", item.get('episode_text'))
        print("LİNK SLUG:", item.get('used_slug') or item.get('slug'))
        print("AFİŞ POSTER:", item.get('object_poster_url') or item.get('series_poster_url') or item.get('poster_url'))

inspect_list('getEpisodesOnBrandAll')
inspect_list('getTrendSeries')
inspect_list('getTrendSeriesL')
inspect_list('getSeriesByAdvancedSliderWithDetail')
inspect_list('allPopularSeries')
inspect_list('getLastSeriesAll')
