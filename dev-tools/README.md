# Eklenti Geliştirme ve Test Araçları (Dev Tools)

Bu klasör, CloudStream eklentileri (özelikle anti-bot, Cloudflare, DDoS-Guard korumalı siteler) için HTML yapısını, oynatıcı (iframe) kaynaklarını ve JSON verilerini analiz etmek amacıyla oluşturulmuştur.

## Kurulum
Python 3 kullanarak test senaryolarını çalıştırmak için aşağıdaki adımları izleyin:

```bash
cd dev-tools
python3 -m venv venv
source venv/bin/activate
pip install curl_cffi beautifulsoup4 playwright
playwright install chromium
```

## Araçların Kullanımı

1. **`fetch.py`**:
   - `curl_cffi` kullanarak modern bir tarayıcı (Chrome) taklidi yapar. Basit JS engellerini aşarak sayfanın statik HTML kaynağını `page.html` dosyasına kaydeder.

2. **`extract_player.py` & `extract_json_full.py`**:
   - Sayfa kaynağında gizlenmiş veya Base64/AES ile şifrelenmiş (Örn: Next.js `__NEXT_DATA__` veya `secureData`) oynatıcı ve bölüm URL'lerini bulmaya yarar. Regex tabanlı veri çekme (scraping) simülasyonları içerir.

3. **`playwright_test2.py` (En Önemlisi)**:
   - Gerçek bir Chromium tarayıcısını başsız (headless) olarak ayağa kaldırır. CloudStream'in **`WebViewResolver`** mantığının birebir aynısını çalıştırır. Sayfadaki JavaScript'in (Hydration) yüklenmesini bekleyip, DOM manipülasyonundan sonra ekrana gömülen gerçek video iframe adresini (`playerLsDizilla`) ve bölümleri test eder.

Eğer yeni bir site (Örn: DiziPal, DiziBox vb.) eklerseniz, site linkini scriptler içindeki `url = "..."` kısmıyla değiştirerek kodlamaya başlamadan önce hedefinizi kusursuzca test edebilirsiniz.
