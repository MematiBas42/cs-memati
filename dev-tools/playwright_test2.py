from playwright.sync_api import sync_playwright

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"

print(f"Test URL'si: {url}")
print("Tarayıcı başlatılıyor (CloudStream WebViewResolver simülasyonu)...")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    
    # Sadece iframe'in veya belli bir class'ın yüklenmesini bekle (networkidle çok uzun sürebilir)
    print("Sayfaya gidiliyor...")
    try:
        page.goto(url, wait_until="domcontentloaded", timeout=15000)
    except:
        print("Uyarı: sayfa tamamen yüklenmedi ama işlem devam ediyor.")
        
    print("playerLsDizilla bekleniyor...")
    try:
        page.wait_for_selector("div#playerLsDizilla", timeout=10000)
    except:
        pass

    print("\n--- TEST: ANA VİDEO KAYNAĞI ---")
    iframe = page.query_selector("div#playerLsDizilla iframe")
    if iframe:
        src = iframe.get_attribute("src")
        print(f"[BAŞARILI] Gerçek video bağlantısı yakalandı: {src}")
    else:
        print("[HATA] Ana video (iframe) yakalanamadı!")
        
    print("\n--- TEST: ALTERNATİF KAYNAKLAR ---")
    alts = page.query_selector_all("a[href*='player']")
    if alts:
        for idx, a in enumerate(alts):
            print(f"Alternatif {idx+1}: Link -> {a.get_attribute('href')}, Metin -> {a.inner_text()}")
    else:
        print("Uyarı: Alternatif player linki bulunamadı.")
        
    print("\n--- TEST: BÖLÜM DOĞRULAMA ---")
    episodes = page.query_selector_all("a[href*='-sezon-']")
    if episodes:
        print(f"[BAŞARILI] Toplam {len(episodes)} bölüm linki bulundu. (Örnek: {episodes[0].get_attribute('href')})")
    
    browser.close()
