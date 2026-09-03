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

print("1. TÜM BÖLÜMLER:")
d1 = get_dec("https://dizilla.now/tum-bolumler")
res1 = d1.get('content', {}).get('result', {})
fr1 = res1.get('FindedResult', {}).get('result', {})
if 'data' in fr1:
    print(f" -> VERİLER BULUNDU! ({len(fr1['data'])} Adet)")
    print(f" -> Örnek Ad: {fr1['data'][0].get('series_name')} - {fr1['data'][0].get('episode_text')}")
    print(f" -> Örnek Slug: {fr1['data'][0].get('used_slug')}")

print("\n2. KATEGORİ (AKSİYON):")
d2 = get_dec("https://dizilla.now/dizi-turu/aksiyon")
res2 = d2.get('content', {}).get('result', {})
fr2 = res2.get('FindedResult', {}).get('result', {})
if 'data' in fr2:
    print(f" -> VERİLER BULUNDU! ({len(fr2['data'])} Adet)")
    print(f" -> Örnek Ad: {fr2['data'][0].get('object_name')}")
    print(f" -> Örnek Slug: {fr2['data'][0].get('used_slug')}")
