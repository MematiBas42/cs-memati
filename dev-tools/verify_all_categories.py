import hashlib, base64, json, re
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad
from curl_cffi import requests

key = base64.b64encode(hashlib.sha256(b"!!22xx!!90!!").digest()).decode('utf-8')[:32].encode('utf-8')
iv = bytes([0] * 16)

# Siteden veriyi çekip şifreyi çözüyoruz (Kotlin'deki decryptDizilla mantığı)
r = requests.get("https://dizilla.now/", impersonate="chrome110")
match = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', r.text, re.DOTALL)
data = json.loads(match.group(1))
secure = data.get("props", {}).get("pageProps", {}).get("secureData", "")
dec = unpad(AES.new(key, AES.MODE_CBC, iv).decrypt(base64.b64decode(secure)), AES.block_size).decode('utf-8')
parsed = json.loads(dec[dec.find('{'):])

# Kotlin'deki when(request.name) listemizin birebir karşılığı
categories = {
    "Son Eklenen Bölümler": parsed.get('getEpisodesOnBrandAll'),
    "Son Eklenen Diziler": parsed.get('getLastSeriesAll'),
    "Trend Diziler": parsed.get('getTrendSeries'),
    "Popüler Diziler": parsed.get('allPopularSeries', {}).get('items') if isinstance(parsed.get('allPopularSeries'), dict) else None,
    "Yeni Başlayan Diziler": parsed.get('getEpisodesOnNewSeries'),
    "Yeni Sezonlar": parsed.get('getEpisodesOnNewSeason')
}

print("--- 6 KATEGORİ MANUEL DOĞRULAMA TESTİ ---")
for cat_name, itemsList in categories.items():
    if itemsList and isinstance(itemsList, list) and len(itemsList) > 0:
        print(f"\n[BAŞARILI] {cat_name} | {len(itemsList)} Dizi/Bölüm çekildi.")
        
        # Kotlin'deki null-check ve değişken atama mantığımız
        item = itemsList[0]
        slug = item.get('used_slug') or item.get('episode_used_slug')
        
        title_base = item.get('culture_title') or item.get('original_title') or item.get('object_name') or item.get('series_name')
        epText = item.get('episode_text') or item.get('season_text') or ""
        finalTitle = f"{title_base} - {epText}" if epText else title_base
        
        posterUrl = item.get('poster_url') or item.get('object_poster_url') or item.get('series_poster_url')
        
        print(f" -> Başlık : {finalTitle}")
        print(f" -> Link   : https://dizilla.now/{slug}")
        print(f" -> Afiş   : {posterUrl}")
    else:
        print(f"\n[HATA] Kategori çöktü veya boş geldi: {cat_name}")
