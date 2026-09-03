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

def print_tree(obj, indent=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ['SiteJsonLd', 'FindedTypeJsonLd']: continue
            if isinstance(v, list):
                print(f"{indent}{k} (Liste: {len(v)} öğe)")
                if len(v) > 0 and isinstance(v[0], dict):
                    print(f"{indent}  Örnek İçerik: {list(v[0].keys())[:5]}")
            elif isinstance(v, dict):
                print(f"{indent}{k} (Obje)")
                print_tree(v, indent + "  ")

print("--- /tum-bolumler İÇİN DERİN TARAMA ---")
d = get_dec("https://dizilla.now/tum-bolumler")
res = d.get('content', {}).get('result', {})

print("[FindedResult Düğümü]")
print_tree(res.get('FindedResult', {}))

print("\n[RelatedResults Düğümü]")
print_tree(res.get('RelatedResults', {}))
