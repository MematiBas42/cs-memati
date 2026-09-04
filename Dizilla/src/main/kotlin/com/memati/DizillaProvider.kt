package com.memati

import android.util.Log
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import android.util.Base64

class DizillaProvider : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)
    override var sequentialMainPage = true

    // SIZMA TESTI (AES-256-CBC Decryptor)
    private fun decryptDizilla(encryptedData: String): String? {
        return try {
            val salt = "!!22xx!!90!!".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(salt)
            val base64Key = Base64.encodeToString(digest, Base64.NO_WRAP)
            val keyBytes = base64Key.substring(0, 32).toByteArray(Charsets.UTF_8)
            val ivBytes = ByteArray(16)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKeySpec = SecretKeySpec(keyBytes, "AES")
            val ivParameterSpec = IvParameterSpec(ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("DZL", "AES Hatası: ${e.message}")
            null
        }
    }

    // ANA SAYFA VİTRİNİNİ (NETFLIX GİBİ) ZENGİNLEŞTİRİYORUZ
    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Son Eklenen Bölümler",
        "${mainUrl}/" to "Son Eklenen Diziler",
        "${mainUrl}/" to "Trend Diziler",
        "${mainUrl}/" to "Popüler Diziler",
        "${mainUrl}/" to "Yeni Başlayan Diziler",
        "${mainUrl}/" to "Yeni Sezonlar"
    )

    private fun cleanDizillaTitle(title: String): String {
        return title.split("|")[0]
            .split(Regex("(?i)türkçe dublaj"))[0]
            .split(Regex("(?i)türkçe altyazılı"))[0]
            .split(Regex("(?i)altyazılı"))[0]
            .split(Regex("(?i)- dizilla"))[0]
            .trim(' ', '-')
    }

    private fun cleanDizillaImage(url: String?, isPoster: Boolean = true): String? {
        if (url.isNullOrBlank()) return null
        
        // 1. Google AMP Proxy'sini atlatıp doğrudan asıl resim sunucusuna iniyoruz.
        var cleanUrl = url.replace(Regex("^https?://[^/]+\\.cdn\\.ampproject\\.org/i/s/"), "https://")
        
        // 2. Dizilla'nın hatalı "Full Image" (/f/f/100/) kırık link (404) bug'ını aşmak için,
        // kendi Resizer (CDN) uç noktalarını belirli boyutlarla tetikliyoruz.
        if (cleanUrl.contains("/f/f/100/")) {
            val dimensions = if (isPoster) "/360/540/100/" else "/1920/1080/100/"
            cleanUrl = cleanUrl.replace("/f/f/100/", dimensions)
        }
        
        return fixUrlNull(cleanUrl)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val results = mutableListOf<SearchResponse>()
        try {
            val responseText = app.get(request.data).text
            val jsonMatch = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(responseText)
            
            if (jsonMatch != null) {
                val dataObj = AppUtils.parseJson<Map<String, Any>>(jsonMatch.groupValues[1])
                val props = dataObj["props"] as? Map<String, Any>
                val pageProps = props?.get("pageProps") as? Map<String, Any>
                val secureData = pageProps?.get("secureData") as? String

                if (!secureData.isNullOrEmpty()) {
                    val decrypted = decryptDizilla(secureData)
                    if (decrypted != null) {
                        val jsonStart = decrypted.indexOf("{")
                        if (jsonStart != -1) {
                            val cleanJson = decrypted.substring(jsonStart)
                            val parsed = AppUtils.parseJson<DizillaSecureData>(cleanJson)
                            
                            // Hangi kategori istendiyse ona uygun Listeyi çekiyoruz
                            val itemsList = when (request.name) {
                                "Son Eklenen Bölümler" -> parsed.getEpisodesOnBrandAll
                                "Son Eklenen Diziler" -> parsed.getLastSeriesAll
                                "Trend Diziler" -> parsed.getTrendSeries
                                "Popüler Diziler" -> parsed.allPopularSeries?.items
                                "Yeni Başlayan Diziler" -> parsed.getEpisodesOnNewSeries
                                "Yeni Sezonlar" -> parsed.getEpisodesOnNewSeason
                                else -> parsed.getEpisodesOnBrandAll
                            }
                            
                            itemsList?.forEach { item ->
                                // Dizilerde used_slug, Bölümlerde episode_used_slug döner
                                val slug = item.usedSlug ?: item.episodeUsedSlug ?: return@forEach
                                val targetUrl = if (slug.startsWith("/")) "$mainUrl$slug" else "$mainUrl/$slug"
                                
                                val rawTitle = item.originalTitle ?: item.objectName ?: item.seriesName ?: return@forEach
                                val cleanTitle = cleanDizillaTitle(rawTitle)
                                
                                val finalTitle = if (request.name == "Son Eklenen Bölümler") {
                                    val sText = item.seasonText ?: ""
                                    val eText = item.episodeText ?: ""
                                    val extra = listOf(sText, eText).filter { it.isNotBlank() }.joinToString(" ")
                                    if (extra.isNotBlank()) "$cleanTitle $extra" else cleanTitle
                                } else {
                                    cleanTitle
                                }

                                val posterUrl = cleanDizillaImage(item.posterUrl ?: item.objectPosterUrl ?: item.seriesPosterUrl ?: item.brandUrl ?: item.faceUrl ?: item.squareUrl, isPoster = true)
                                
                                results.add(newTvSeriesSearchResponse(finalTitle, targetUrl, TvType.TvSeries) {
                                    this.posterUrl = posterUrl
                                    if (item.imdbPoint != null && item.imdbPoint > 0.0) {
                                        this.score = Score.from(item.imdbPoint, 10)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("DZL", "Ana Sayfa AES Hatası: ${e.message}") }

        return newHomePageResponse(request.name, results)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val res = app.post(
                "$mainUrl/api/bg/searchContent",
                params = mapOf("searchterm" to query),
                headers = mapOf("Accept" to "application/json, text/plain, */*", "X-Requested-With" to "XMLHttpRequest")
            )
            val encryptedResponse = res.parsedSafe<DizillaSearchResponse>()?.response
            if (!encryptedResponse.isNullOrEmpty()) {
                val decrypted = decryptDizilla(encryptedResponse)
                if (decrypted != null) {
                    val jsonStart = decrypted.indexOf("{")
                    if (jsonStart != -1) {
                        val parsed = AppUtils.parseJson<DizillaSearchResult>(decrypted.substring(jsonStart))
                        parsed.result?.forEach { item ->
                            val title = cleanDizillaTitle(item.title ?: return@forEach)
                            val slug = item.slug ?: return@forEach
                            val targetUrl = if (slug.startsWith("/")) "$mainUrl$slug" else "$mainUrl/$slug"
                            val posterUrl = cleanDizillaImage(item.poster, isPoster = true)
                            results.add(newTvSeriesSearchResponse(title, targetUrl, TvType.TvSeries) { this.posterUrl = posterUrl })
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("DZL", "Arama AES Hatası: ${e.message}") }
        return results.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val rawTitle = document.selectFirst("meta[property='og:title']")?.attr("content")?.replace(" izle", "")?.trim()
            ?: document.selectFirst("title")?.text()?.replace(" izle", "")?.split("-")?.firstOrNull()?.trim() ?: return null
        
        val title = cleanDizillaTitle(rawTitle)
        
        val rawPoster = document.selectFirst("meta[property='og:image']")?.attr("content")
        val description = document.selectFirst("meta[property='og:description']")?.attr("content")?.trim() 
        val htmlTags = document.select("a[href*='/dizi-turu/']").map { it.text() }
        val htmlYear = document.select("span").find { it.text().matches(Regex("\\d{4}")) }?.text()?.toIntOrNull()

        var dizillaData: DizillaSecureData? = null
        val episodeList = mutableListOf<Episode>()
        
        // TMDB Data Storage
        var tmdbDetails: TmdbDetails? = null
        var tmdbSeasonsMap: Map<Int, TmdbSeasonResp?> = emptyMap()
        
        try {
            val jsonMatch = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(document.html())
            if (jsonMatch != null) {
                val dataObj = AppUtils.parseJson<Map<String, Any>>(jsonMatch.groupValues[1])
                val secureData = (dataObj["props"] as? Map<String, Any>)?.get("pageProps")?.let { (it as? Map<String, Any>)?.get("secureData") as? String }
                if (!secureData.isNullOrEmpty()) {
                    val decrypted = decryptDizilla(secureData)
                    if (decrypted != null) {
                        val jsonStart = decrypted.indexOf("{")
                        if (jsonStart != -1) {
                            val parsed = AppUtils.parseJson<DizillaSecureData>(decrypted.substring(jsonStart))
                            dizillaData = parsed
                            
                            // 🚀 ENDÜSTRİYEL TMDB ENTEGRASYONU (Asenkron & Hızlı)
                            val imdbId = parsed.relatedResults?.getSeriesByImdb?.result?.firstOrNull()?.imdbId
                            if (!imdbId.isNullOrBlank()) {
                                val apiKey = "c4ffcab48dfaa7b41625ac13d61aec31"
                                val tmdbId = app.get("https://api.themoviedb.org/3/find/$imdbId?api_key=$apiKey&external_source=imdb_id&language=tr-TR").parsedSafe<TmdbFindResponse>()?.tvResults?.firstOrNull()?.id
                                
                                if (tmdbId != null) {
                                    tmdbDetails = app.get("https://api.themoviedb.org/3/tv/$tmdbId?api_key=$apiKey&language=tr-TR&append_to_response=credits").parsedSafe<TmdbDetails>()
                                    
                                    val seasonNumbers = parsed.relatedResults?.getSerieSeasonAndEpisodes?.result?.mapNotNull { it.seasonNo } ?: emptyList()
                                    
                                    val mapped = seasonNumbers.amap { sNo ->
                                        sNo to app.get("https://api.themoviedb.org/3/tv/$tmdbId/season/$sNo?api_key=$apiKey&language=tr-TR").parsedSafe<TmdbSeasonResp>()
                                    }
                                    tmdbSeasonsMap = mapped.toMap()
                                }
                            }

                            parsed.relatedResults?.getSerieSeasonAndEpisodes?.result?.forEach { season ->
                                val tmdbSeason = tmdbSeasonsMap[season.seasonNo]?.episodes
                                season.episodes?.forEach { ep ->
                                    if (ep.usedSlug != null) {
                                        val tmdbEp = tmdbSeason?.find { it.episodeNumber == ep.episodeNo }
                                        
                                        episodeList.add(newEpisode("$mainUrl/${ep.usedSlug}") {
                                            this.name = tmdbEp?.name ?: ep.episodeText ?: "Bölüm ${ep.episodeNo}"
                                            this.season = season.seasonNo
                                            this.episode = ep.episodeNo
                                            this.description = tmdbEp?.overview?.takeIf { it.isNotBlank() } ?: ep.episodeDescription
                                            
                                            // TMDB Kapak Resmi
                                            if (!tmdbEp?.stillPath.isNullOrBlank()) {
                                                this.posterUrl = "https://image.tmdb.org/t/p/w500${tmdbEp?.stillPath}"
                                            }
                                            
                                            // TMDB Süre (Runtime)
                                            if (tmdbEp?.runtime != null) {
                                                this.runTime = tmdbEp.runtime
                                            }
                                            
                                            val rawDate = tmdbEp?.airDate ?: ep.releaseDate?.split("T")?.firstOrNull()
                                            if (!rawDate.isNullOrBlank()) {
                                                addDate(rawDate)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        if (episodeList.isEmpty()) {
            val allLinks = document.select("a[href]").mapNotNull { fixUrlNull(it.attr("href")) }.distinct()
            val episodeUrls = allLinks.filter { it.contains("-sezon-") && it.contains(mainUrl) }
            episodeUrls.forEach { epHref ->
                val epSeason = Regex("-(\\d+)-sezon-").find(epHref)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val epEpisode = Regex("-(\\d+)-bolum").find(epHref)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (epSeason != null && epEpisode != null) {
                    episodeList.add(newEpisode(epHref) {
                        this.name = "Bölüm $epEpisode"
                        this.season = epSeason
                        this.episode = epEpisode
                    })
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList.distinctBy { it.data }.sortedBy { it.episode }) {
            this.posterUrl = cleanDizillaImage(rawPoster, isPoster = true)
            this.backgroundPosterUrl = cleanDizillaImage(rawPoster, isPoster = false)
            
            // 1. TMDB Yıl Tespiti (Fallback: Dizilla JSON -> HTML)
            this.year = tmdbDetails?.firstAirDate?.substringBefore("-")?.toIntOrNull()
                ?: dizillaData?.contentItem?.releaseYear
                ?: htmlYear

            this.plot = dizillaData?.contentItem?.usedShortDescription?.takeIf { it.isNotBlank() }
                ?: dizillaData?.contentItem?.description?.takeIf { it.isNotBlank() }
                ?: dizillaData?.contentItem?.usedLongDescription?.takeIf { it.isNotBlank() }
                ?: description
                
            // 2. TMDB Tür (Genre) Tespiti ve Zeki Dil Etiketleri
            val tmdbGenres = tmdbDetails?.genres?.mapNotNull { it.name } ?: emptyList()
            val dizillaCategories = dizillaData?.contentItem?.categories?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            
            val combinedSeo = "${rawTitle} ${description ?: ""}".lowercase()
            val languageTags = mutableListOf<String>()
            if (combinedSeo.contains("dublaj")) languageTags.add("🇹🇷 Dublaj")
            if (combinedSeo.contains("altyaz")) languageTags.add("🇹🇷 Altyazı")
            
            val finalTags = (tmdbGenres.takeIf { it.isNotEmpty() } ?: dizillaCategories.takeIf { it.isNotEmpty() } ?: htmlTags) + languageTags
            this.tags = finalTags.distinct()
            
            // Native Dizilla + TMDB Actor Mapping
            val allActors = mutableListOf<Pair<Actor, String?>>()
            
            // 1. TMDB'den gelen devasa ve resimli kadroyu ekle
            tmdbDetails?.credits?.cast?.forEach { cast ->
                if (!cast.name.isNullOrBlank()) {
                    val profileImg = cast.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
                    allActors.add(Pair(Actor(cast.name, profileImg), cast.character))
                }
            }
            
            // 2. Dizilla'nın Yaratıcılarını Ekle
            dizillaData?.relatedResults?.getSerieCreatorsById?.result?.forEach { cast ->
                if (!cast.name.isNullOrBlank()) {
                    // TMDB'de zaten varsa atla
                    if (allActors.none { it.first.name == cast.name }) {
                        allActors.add(Pair(Actor(cast.name, cleanDizillaImage(cast.castImage, isPoster = true)), "Yaratıcı"))
                    }
                }
            }
            
            // 3. Eğer TMDB çalışmadıysa (API limiti vb.) Native Dizilla Oyuncularına Düş (Fallback)
            if (tmdbDetails == null) {
                dizillaData?.relatedResults?.getSerieCastsById?.result?.forEach { cast ->
                    if (!cast.name.isNullOrBlank()) {
                        allActors.add(Pair(Actor(cast.name, cleanDizillaImage(cast.castImage, isPoster = true)), cast.roleName))
                    }
                }
            }
            
            if (allActors.isNotEmpty()) {
                addActors(allActors)
            }
            
            dizillaData?.contentItem?.imdbPoint?.let { imdb ->
                if (imdb > 0.0) {
                    this.score = Score.from(imdb, 10)
                }
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val iframes = mutableSetOf<String>()
        try {
            val response = app.get(data)
            val jsonMatch = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(response.text)
            if (jsonMatch != null) {
                val dataObj = AppUtils.parseJson<Map<String, Any>>(jsonMatch.groupValues[1])
                val secureData = (dataObj["props"] as? Map<String, Any>)?.get("pageProps")?.let { (it as? Map<String, Any>)?.get("secureData") as? String }
                if (!secureData.isNullOrEmpty()) {
                    val decrypted = decryptDizilla(secureData)
                    if (decrypted != null) {
                        val jsonStart = decrypted.indexOf("{")
                        if (jsonStart != -1) {
                            val parsed = AppUtils.parseJson<DizillaSecureData>(decrypted.substring(jsonStart))
                            parsed.relatedResults?.getEpisodeSources?.result?.forEach { src ->
                                val content = src.sourceContent ?: return@forEach
                                val iframeMatch = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']").find(content)
                                if (iframeMatch != null) {
                                    val rawUrl = iframeMatch.groupValues[1]
                                    val finalUrl = if (rawUrl.startsWith("//")) "https:$rawUrl" else rawUrl
                                    if (finalUrl !in iframes) {
                                        iframes.add(finalUrl)
                                        var linkFound = false
                                        loadExtractor(finalUrl, "$mainUrl/", subtitleCallback) { link ->
                                            linkFound = true
                                            callback.invoke(link)
                                        }
                                        if (!linkFound) {
                                            val domain = Regex("https?://([^/]+)").find(finalUrl)?.groupValues?.getOrNull(1) ?: "Bilinmeyen"
                                            val altName = src.sourceName ?: "Alternatif"
                                            val badge = "⚠️ $altName ($domain)"
                                            
                                            callback.invoke(
                                                newExtractorLink("Dizilla", badge, finalUrl) {
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        if (iframes.isEmpty()) {
            val document = app.get(data, interceptor = WebViewResolver(Regex("iframe|player|video", RegexOption.IGNORE_CASE))).document
            val rawIframeUrl = fixUrlNull(document.selectFirst("div#playerLsDizilla iframe")?.attr("src")) ?: fixUrlNull(document.selectFirst("iframe[src*='player']")?.attr("src"))
            val finalIframeUrl = if (rawIframeUrl != null && rawIframeUrl.startsWith("//")) "https:$rawIframeUrl" else rawIframeUrl
            if (finalIframeUrl != null && finalIframeUrl !in iframes) {
                iframes.add(finalIframeUrl)
                loadExtractor(finalIframeUrl, "$mainUrl/", subtitleCallback, callback)
            }
        }
        return iframes.isNotEmpty()
    }
}
