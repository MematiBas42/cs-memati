

package com.memati

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class SezonlukDizi : MainAPI() {
    override var mainUrl              = "https://sezonlukdizi.cc"
    override var name                 = "SezonlukDizi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    private var cachedAspData: AspData? = null
    
    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id&s="          to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=2&s="    to "Yerli Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s="    to "Yabancı Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s="    to "Asya Dizileri",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s="    to "Animasyonlar",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s="    to "Animeler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s="    to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.afis a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val imdbText  = this.selectFirst("span.imdbp")?.text()?.substringAfter("IMDb")?.trim()
        val score     = imdbText?.replace(",", ".")?.toDoubleOrNull()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { 
            this.posterUrl = posterUrl 
            if (score != null && score > 0.0) {
                this.score = Score.from(score, 10)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.post(
            "${mainUrl}/ajax/arama.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf("q" to query)
        ).parsedSafe<SearchData>() ?: return emptyList()

        val diziler = res.results?.diziler?.results ?: emptyList()
        return diziler.mapNotNull { item ->
            val title = item.title ?: return@mapNotNull null
            val url = item.url ?: return@mapNotNull null
            val image = item.image

            newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = if (image != null) fixUrlNull(image) else null
                if (item.imdb != null && item.imdb > 0.0) {
                    this.score = Score.from(item.imdb, 10)
                }
            }
        }
    }

    data class SearchData(
        val results: SearchCategories? = null
    )

    data class SearchCategories(
        val diziler: SearchCategory? = null
    )

    data class SearchCategory(
        val results: List<SearchItem>? = null
    )

    data class SearchItem(
        val title: String? = null,
        val url: String? = null,
        val image: String? = null,
        val imdb: Double? = null
    )

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        if (url.startsWith("${mainUrl}/search-intent/")) {
            val query = java.net.URLDecoder.decode(url.substringAfter("search-intent/"), "UTF-8")
            val searchResult = search(query).firstOrNull()?.url
            if (searchResult != null) {
                return load(searchResult)
            } else {
                // Eğer sitede bulunamazsa CloudStream'i çökertmemek için sahte/boş bir sayfa döndürüyoruz.
                return newTvSeriesLoadResponse(query, url, TvType.TvSeries, emptyList()) {
                    this.plot = "Bu dizi SezonlukDizi kaynağında bulunamadı."
                    this.posterUrl = "https://via.placeholder.com/500x750.png?text=Bulunamadi"
                }
            }
        }

        val endpoint = url.split("/").last()

        val parallelReqs = listOf(
            url,
            "${mainUrl}/oyuncular/${endpoint}",
            "${mainUrl}/bolumler/${endpoint}"
        ).amap { 
            try { app.get(it).document } catch (e: Exception) { null } 
        }

        val document    = parallelReqs[0] ?: return null
        val actorsReq   = parallelReqs[1]
        val episodesReq = parallelReqs[2]

        val title       = document.selectFirst("div.header")?.text()?.trim() ?: "Bilinmeyen Dizi"
        val poster      = fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: ""
        val htmlYear    = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val htmlTags    = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val duration    = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim().substringBefore(" Dk.").toIntOrNull()
        
        val imdbHref = document.selectFirst("a.imdb")?.attr("href")
        val imdbId = imdbHref?.split("/")?.firstOrNull { it.startsWith("tt") }
        
        var tmdbBackdrop: String? = null
        var tmdbPlot: String? = null
        var tmdbYear: Int? = null
        var tmdbSeasonsMap: Map<Int, TmdbSeasonResp?> = emptyMap()
        var tmdbVoteAverage: Double? = null
        var tmdbTrailer: String? = null
        var tmdbRecs: List<SearchResponse>? = null
        val tmdbActors = mutableListOf<Pair<Actor, String?>>()
        val tmdbTags = mutableListOf<String>()

        if (!imdbId.isNullOrBlank()) {
            try {
                val apiKey = "c4ffcab48dfaa7b41625ac13d61aec31"
                val tmdbFind = app.get("https://api.themoviedb.org/3/find/$imdbId?api_key=$apiKey&external_source=imdb_id&language=tr-TR").parsedSafe<TmdbFindResponse>()
                val tmdbTvId = tmdbFind?.tvResults?.firstOrNull()?.id
                
                if (tmdbTvId != null) {
                    val tmdbDetails = app.get("https://api.themoviedb.org/3/tv/$tmdbTvId?api_key=$apiKey&language=tr-TR&append_to_response=credits,videos,recommendations&include_video_language=tr,en,null").parsedSafe<TmdbDetails>()
                    
                    tmdbBackdrop = tmdbDetails?.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                    tmdbPlot = tmdbDetails?.overview?.takeIf { it.isNotBlank() }
                    tmdbYear = tmdbDetails?.firstAirDate?.substringBefore("-")?.toIntOrNull()
                    tmdbDetails?.genres?.forEach { g -> g.name?.let { tmdbTags.add(it) } }
                    
                    tmdbDetails?.credits?.cast?.take(10)?.forEach { cast ->
                        if (!cast.name.isNullOrBlank()) {
                            val profileImg = cast.profilePath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            tmdbActors.add(Pair(Actor(cast.name, profileImg), cast.character))
                        }
                    }
                    // TMDB Season Parity
                    val htmlSeasons = episodesReq?.select("table.unstackable")?.mapIndexed { index, _ -> index + 1 } ?: emptyList()
                    val mapped = htmlSeasons.amap { sNo ->
                        sNo to app.get("https://api.themoviedb.org/3/tv/$tmdbTvId/season/$sNo?api_key=$apiKey&language=tr-TR").parsedSafe<TmdbSeasonResp>()
                    }
                    tmdbSeasonsMap = mapped.toMap()
                    tmdbVoteAverage = tmdbDetails?.voteAverage
                    
                    tmdbTrailer = tmdbDetails?.videos?.results?.find { it.site == "YouTube" && it.type == "Trailer" }?.key
                    tmdbRecs = tmdbDetails?.recommendations?.results?.mapNotNull { rec ->
                        val recTitle = rec.name ?: return@mapNotNull null
                        val recPoster = rec.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        newTvSeriesSearchResponse(recTitle, "${mainUrl}/search-intent/${recTitle}", TvType.TvSeries) {
                            this.posterUrl = recPoster
                        }
                    }

                }
            } catch (e: Exception) {}
        }

        val finalActors: List<Pair<Actor, String?>> = if (tmdbActors.isNotEmpty()) tmdbActors else {
            actorsReq?.select("div.doubling div.ui")?.mapNotNull {
                val actorName = it.selectFirst("div.header")?.text()?.trim() ?: return@mapNotNull null
                Pair(Actor(actorName, fixUrlNull(it.selectFirst("img")?.attr("src"))), null)
            } ?: emptyList()
        }

        val episodes = mutableListOf<Episode>()
        val sezonlar = episodesReq?.select("table.unstackable") ?: emptyList<Element>()
        for (sezon in sezonlar) {
            for (bolum in sezon.select("tbody tr")) {
                val epName    = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref    = fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode = bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")?.trim()?.toIntOrNull()
                val epSeason  = bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()

                val tmdbEp = tmdbSeasonsMap[epSeason]?.episodes?.find { it.episodeNumber == epEpisode }

                episodes.add(newEpisode(epHref) {
                    this.name    = tmdbEp?.name ?: epName
                    this.season  = epSeason
                    this.episode = epEpisode
                    this.description = tmdbEp?.overview?.takeIf { it.isNotBlank() }
                    
                    if (!tmdbEp?.stillPath.isNullOrBlank()) {
                        this.posterUrl = "https://image.tmdb.org/t/p/w500${tmdbEp?.stillPath}"
                    }
                    if (tmdbEp?.runtime != null) {
                        this.runTime = tmdbEp.runtime
                    }
                    if (!tmdbEp?.airDate.isNullOrBlank()) {
                        addDate(tmdbEp?.airDate)
                    }
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = tmdbBackdrop ?: poster
            this.year      = tmdbYear ?: htmlYear
            this.plot      = tmdbPlot ?: description
            this.tags      = tmdbTags.takeIf { it.isNotEmpty() } ?: htmlTags
            this.duration  = duration
            
            if (tmdbVoteAverage != null && tmdbVoteAverage > 0.0) {
                this.score = Score.from(tmdbVoteAverage, 10)
            } else {
                val cleanScore = document.selectFirst("a.imdb")?.text()?.substringAfter("IMDb:")?.trim()?.toDoubleOrNull()
                if (cleanScore != null && cleanScore > 0.0) {
                    this.score = Score.from(cleanScore, 10)
                }
            }
            
            if (tmdbTrailer != null) {
                val tUrl = "https://www.youtube.com/watch?v=$tmdbTrailer"
                addTrailer(tUrl)
            }
            if (tmdbRecs != null && tmdbRecs.isNotEmpty()) {
                this.recommendations = tmdbRecs
            }
            
            addActors(finalActors)
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val aspData = getAspData()
        val bid = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false

        val langs = listOf("1" to "AltYazı", "0" to "Dublaj")
        
        langs.amap { (dilKodu, dilAdi) ->
            val response = app.post(
                "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data    = mapOf("bid" to bid, "dil" to dilKodu)
            ).parsedSafe<Kaynak>()

            response?.takeIf { it.status == "success" }?.data?.amap { veri ->
                val veriResponse = app.post(
                    "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    data    = mapOf("id" to "${veri.id}")
                ).document

                val iframe = fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src"))
                if (iframe != null) {
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                        callback.invoke(
                            ExtractorLink(
                                source        = "$dilAdi - ${veri.baslik}",
                                name          = "$dilAdi - ${veri.baslik}",
                                url           = link.url,
                                referer       = link.referer,
                                quality       = link.quality,
                                headers       = link.headers,
                                extractorData = link.extractorData,
                                type          = link.type
                            )
                        )
}

                }
            }
        }
        return true
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData(): AspData {
        cachedAspData?.let { return it }
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js")
        val dataAlternatifAsp = Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1).toString()
        val dataEmbedAsp = Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1).toString()
        val newData = AspData(dataAlternatifAsp, dataEmbedAsp)
        cachedAspData = newData
        return newData
    }
}


data class TmdbFindResponse(
    @com.fasterxml.jackson.annotation.JsonProperty("tv_results") val tvResults: List<TmdbTvResult>? = null
)

data class TmdbTvResult(
    @com.fasterxml.jackson.annotation.JsonProperty("id") val id: Int? = null
)

data class TmdbDetails(
    @com.fasterxml.jackson.annotation.JsonProperty("backdrop_path") val backdropPath: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("overview") val overview: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("first_air_date") val firstAirDate: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("vote_average") val voteAverage: Double? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("credits") val credits: TmdbCredits? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("genres") val genres: List<TmdbGenre>? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("videos") val videos: TmdbVideos? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("recommendations") val recommendations: TmdbRecommendations? = null
)

data class TmdbVideos(
    @com.fasterxml.jackson.annotation.JsonProperty("results") val results: List<TmdbVideo>? = null
)

data class TmdbVideo(
    @com.fasterxml.jackson.annotation.JsonProperty("key") val key: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("site") val site: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("type") val type: String? = null
)

data class TmdbRecommendations(
    @com.fasterxml.jackson.annotation.JsonProperty("results") val results: List<TmdbRecommendation>? = null
)

data class TmdbRecommendation(
    @com.fasterxml.jackson.annotation.JsonProperty("name") val name: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("poster_path") val posterPath: String? = null
)

data class TmdbGenre(
    @com.fasterxml.jackson.annotation.JsonProperty("name") val name: String? = null
)

data class TmdbCredits(
    @com.fasterxml.jackson.annotation.JsonProperty("cast") val cast: List<TmdbCast>? = null
)

data class TmdbCast(
    @com.fasterxml.jackson.annotation.JsonProperty("name") val name: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("profile_path") val profilePath: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("character") val character: String? = null
)

data class TmdbSeasonResp(
    @com.fasterxml.jackson.annotation.JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null
)

data class TmdbEpisode(
    @com.fasterxml.jackson.annotation.JsonProperty("episode_number") val episodeNumber: Int? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("name") val name: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("overview") val overview: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("still_path") val stillPath: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("air_date") val airDate: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("runtime") val runtime: Int? = null
)
