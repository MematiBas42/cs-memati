import hashlib, base64, json, re
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from curl_cffi import requests

key = base64.b64encode(hashlib.sha256(b"!!22xx!!90!!").digest()).decode('utf-8')[:32].encode('utf-8')
iv = bytes([0] * 16)

def get_dec(url):
    r = requests.get(url, impersonate="chrome110")
    match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', r.text, re.DOTALL)
    data = json.loads(match.group(1))
    secure = data.get("props", {}).get("pageProps", {}).get("secureData", "")
    dec = unpad(AES.new(key, AES.MODE_CBC, iv).decrypt(base64.b64decode(secure)), AES.block_size).decode('utf-8')
    return json.loads(dec[dec.find('{'):])

try:
    print("1. TÜM BÖLÜMLER YAPI ANALİZİ (/tum-bolumler)")
    d1 = get_dec("https://dizilla.now/tum-bolumler")
    print(" -> Kök Anahtarlar:", list(d1.keys()))
    
    if 'content' in d1:
        print(" -> 'content' İçi:", list(d1['content'].keys()))
        res = d1['content']['result']
        print(" -> 'result' Türü:", type(res).__name__)
        
        if isinstance(res, dict):
            print(" -> 'result' İçi:", list(res.keys()))
            if 'data' in res:
                print(f" -> 'data' Dizisi Bulundu! Adet: {len(res['data'])}")
                print(f" -> Örnek Veri: {res['data'][0].get('series_name')} - {res['data'][0].get('episode_text')}")
                print(f" -> Dizi Link Slug: {res['data'][0].get('used_slug')}")
        elif isinstance(res, list):
            print(f" -> 'result' Dizisi Bulundu! Adet: {len(res)}")
            print(f" -> Örnek Veri: {res[0].keys()}")
except Exception as e:
    print("Hata:", e)
