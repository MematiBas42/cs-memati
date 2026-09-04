

package com.memati

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class AnimeciX : MainAPI() {
    override var mainUrl              = "https://animecix.tv"
    override var name                 = "AnimeciX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    private val apiHeaders = mapOf("x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk")

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 200L  // ? 0.20 saniye
    override var sequentialMainPageScrollDelay = 200L  // ? 0.20 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "${mainUrl}/secure/titles?type=movie&onlyStreamable=true"  to "Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(
            "${request.data}&page=${page}&perPage=16",
headers = apiHeaders
        ).parsedSafe<Category>()

        val home     = response?.pagination?.data?.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        } ?: listOf<SearchResponse>()

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/secure/search/${query}?limit=20", headers = apiHeaders).parsedSafe<Search>() ?: return listOf()

        return response.results.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) {
                this.posterUrl = fixUrlNull(anime.poster)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(
            url,
headers = apiHeaders
        ).parsedSafe<Title>() ?: return null
        val episodes = mutableListOf<Episode>()
        val titleId  = url.substringAfter("?titleId=")

        if (response.title.titleType == "anime") {
            val allEps = (response.title.seasons ?: emptyList()).amap { sezon ->
                try {
                    val sezonResponse = app.get("${mainUrl}/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=${titleId}", headers = apiHeaders).parsedSafe<TitleVideos>()
                    sezonResponse?.videos?.mapNotNull { video ->
                        newEpisode(video.url) {
                            this.name = video.name ?: "${video.seasonNum}. Sezon ${video.episodeNum}. Bölüm"
                            this.season = video.seasonNum
                            this.episode = video.episodeNum
                            if (!video.thumbnail.isNullOrBlank()) {
                                this.posterUrl = fixUrlNull(video.thumbnail)
                            }
                        }
                    } ?: emptyList()
                } catch (e: Exception) {
                    emptyList<Episode>()
                }
            }.flatten()
            episodes.addAll(allEps)
        } else {
            if (!response.title.videos.isNullOrEmpty()) {
                episodes.add(newEpisode(response.title.videos!!.first().url) {
                    this.name    = "Filmi İzle"
                    this.season  = 1
                    this.episode = 1
                })
            }
        }


        return newTvSeriesLoadResponse(
            response.title.title,
            "${mainUrl}/secure/titles/${response.title.id}?titleId=${response.title.id}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(response.title.poster)
            this.backgroundPosterUrl = fixUrlNull(response.title.backdrop)
            this.year      = response.title.year
            this.plot      = response.title.description
            
            val genres = (response.title.tags ?: emptyList()).mapNotNull { it.name }
            val keywords = (response.title.keywords ?: emptyList()).mapNotNull { it.name }
            this.tags      = (genres + keywords).distinct()
            
            if (response.title.runtime != null && response.title.runtime > 0) {
                this.duration = response.title.runtime
            }
            
            response.title.rating?.toDoubleOrNull()?.let { score ->
                if (score > 0.0) {
                    this.score = Score.from(score, 10)
                }
            }
            
            // AniList ve MAL Tracker (Sync) Entegrasyonu
            this.syncData = mutableMapOf()
            response.title.anilistId?.let { this.syncData!!["anilist"] = it.toString() }
            response.title.malId?.let { this.syncData!!["mal"] = it.toString() }

            addActors((response.title.actors ?: emptyList()).map { Actor(it.name, fixUrlNull(it.poster)) })
            if (!response.title.trailer.isNullOrBlank()) {
                addTrailer("https://www.youtube.com/watch?v=${response.title.trailer}")
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val targetUrl = if (data.startsWith("http")) data else "${mainUrl}/${data.removePrefix("/")}"
        
        try {
            val response = app.get(targetUrl, headers = apiHeaders, referer="${mainUrl}/")
            var iframeLink = response.url
            
            // Eğer HTTP yönlendirmesi gerçekleşmediyse (aynı URL'de kaldıysak)
            // sayfa içeriğindeki iframe'i manuel olarak arayalım.
            if (iframeLink == targetUrl || iframeLink.contains("secure/best-video")) {
                val doc = response.document
                val extractedIframe = doc.selectFirst("iframe")?.attr("src")
                if (!extractedIframe.isNullOrBlank()) {
                    iframeLink = if (extractedIframe.startsWith("http")) extractedIframe else "https:$extractedIframe"
                }
            }
            
            loadExtractor(iframeLink, "${mainUrl}/", subtitleCallback, callback)
        } catch (e: Exception) {
            return false
        }

        return true
    }
}