from playwright.sync_api import sync_playwright

url = "https://dizilla.now/solar-opposites-2-sezon-3-bolum"

print(f"Test URL'si: {url}")
print("Tarayıcı başlatılıyor (CloudStream WebViewResolver simülasyonu)...")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    page = browser.new_page()
    
    # Reklam hostlarını (genelde tracking) reddetmek için basit bir filtre
    page.route("**/*", lambda route: route.abort() if "ads" in route.request.url or "tracker" in route.request.url else route.continue_())
    
    print("Sayfaya gidiliyor ve JS'in çalışması (Hydration) bekleniyor...")
    page.goto(url, wait_until="networkidle")
    
    # Sitenin şifreyi çözüp iframe'i sayfaya eklemesini bekleyelim (CloudStream WebViewResolver Regex beklemesi)
    try:
        page.wait_for_selector("#playerLsDizilla iframe", timeout=10000)
    except:
        print("Uyarı: playerLsDizilla içinde 10 saniye içinde iframe bulunamadı.")
    
    print("\n--- TEST: ANA VİDEO KAYNAĞI ---")
    iframe = page.query_selector("#playerLsDizilla iframe")
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
        print("Uyarı: Alternatif player linki (a[href*='player']) bulunamadı.")
        
    browser.close()
