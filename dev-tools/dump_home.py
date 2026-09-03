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

print("TÜM BÖLÜMLER İÇİN AĞAÇ (RelatedResults):")
d1 = get_dec("https://dizilla.now/tum-bolumler")
res1 = d1.get('content', {}).get('result', {})
if 'RelatedResults' in res1 and res1['RelatedResults']:
    for k, v in res1['RelatedResults'].items():
        if isinstance(v, dict) and 'result' in v:
            r = v['result']
            if isinstance(r, dict) and 'data' in r:
                print(f" -> ANAHTAR BULUNDU: {k} | Boyut: {len(r['data'])}")
                if r['data']: 
                    print(f"    -> Örnek: {r['data'][0].get('series_name')} - {r['data'][0].get('episode_text')}")

print("\nKATEGORİ İÇİN AĞAÇ (RelatedResults):")
d2 = get_dec("https://dizilla.now/dizi-turu/aksiyon")
res2 = d2.get('content', {}).get('result', {})
if 'RelatedResults' in res2 and res2['RelatedResults']:
    for k, v in res2['RelatedResults'].items():
        if isinstance(v, dict) and 'result' in v:
            r = v['result']
            if isinstance(r, dict) and 'data' in r:
                print(f" -> ANAHTAR BULUNDU: {k} | Boyut: {len(r['data'])}")
                if r['data']: 
                    print(f"    -> Örnek: {r['data'][0].get('object_name')} - Slug: {r['data'][0].get('used_slug')}")
