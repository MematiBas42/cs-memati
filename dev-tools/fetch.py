from curl_cffi import requests
from bs4 import BeautifulSoup
import json

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"

print(f"[{url}] Adresine istek atılıyor...")
try:
    # Modern bir Chrome tarayıcı gibi davranıyoruz
    r = requests.get(url, impersonate="chrome")
    
    with open("page.html", "w", encoding="utf-8") as f:
        f.write(r.text)
        
    soup = BeautifulSoup(r.text, 'html.parser')
    
    print("\n--- TEMEL BİLGİLER ---")
    title = soup.find('h1')
    print("Dizi Başlığı:", title.text.strip() if title else "Bulunamadı")
    
    print("\n--- OYNATICI (PLAYER) BİLGİLERİ ---")
    player_div = soup.find('div', id='playerLsDizilla')
    if player_div:
        iframe = player_div.find('iframe')
        print("Ana İframe Src:", iframe.get('src') if iframe else "İframe yok")
    else:
        print("playerLsDizilla div'i bulunamadı. Belki başka class kullanılıyor.")
        # Alternatif player butonları
        alt_players = soup.select("a[href*='player']")
        print(f"Bulunan alternatif oynatıcı bağlantısı: {len(alt_players)}")
        for a in alt_players[:3]:
            print(" -", a.get('href'))
            
    print("\n--- BÖLÜM (EPISODES) LİSTESİ DOM YAPISI ---")
    szn_div = soup.find('div', class_=lambda c: c and 'szn' in c)
    if szn_div:
        print("Örnek Sezon/Bölüm yapısı bulundu:")
        episodes = szn_div.select("div.episodes div.cursor-pointer a.opacity-60")
        for ep in episodes[:3]:
            print(f" - {ep.text.strip()} -> {ep.get('href')}")
    else:
        print("szn (sezon) div'i bulunamadı!")
        
except Exception as e:
    print("Hata oluştu:", str(e))
