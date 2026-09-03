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

d = get_dec("https://dizilla.now/tum-bolumler")
print("KÖK ANAHTARLAR:", list(d.keys()))

if 'content' in d:
    print("\nCONTENT İÇİ:", list(d['content'].keys()))
    res = d['content'].get('result', {})
    if isinstance(res, dict):
        print("\nRESULT İÇİ:", list(res.keys()))
        fr = res.get('FindedResult', {})
        print("\nFINDED_RESULT İÇİ:", list(fr.keys()))
        
        fr_res = fr.get('result', {})
        print("\nFINDED_RESULT -> RESULT İÇİ:", list(fr_res.keys()) if isinstance(fr_res, dict) else "Liste")
        
        if isinstance(fr_res, dict) and 'data' in fr_res:
            print("\nNİHAYET DATA BULUNDU! Adet:", len(fr_res['data']))
            print("Örnek:", fr_res['data'][0].get('series_name'))
