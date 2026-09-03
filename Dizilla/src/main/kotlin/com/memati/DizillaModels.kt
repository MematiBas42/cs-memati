package com.memati

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Enterprise-grade Data Models for Dizilla API Responses.
 */

data class DizillaSearchResult(
    @JsonProperty("state") val state: Boolean? = null,
    @JsonProperty("result") val result: List<DizillaSearchItem>? = null
)

data class DizillaSearchResponse(
    @JsonProperty("response") val response: String? = null
)

data class DizillaSearchItem(
    @JsonProperty("object_name") val title: String? = null,
    @JsonProperty("used_slug") val slug: String? = null,
    @JsonProperty("object_poster_url") val poster: String? = null
)

// --- DECRYPTED SECURE DATA MODELS ---

data class DizillaSecureData(
    // Ana Sayfa Verileri
    @JsonProperty("getEpisodesOnBrandAll") val getEpisodesOnBrandAll: List<DizillaPageItem>? = null,
    @JsonProperty("getLastSeriesAll") val getLastSeriesAll: List<DizillaPageItem>? = null,
    @JsonProperty("getTrendSeries") val getTrendSeries: List<DizillaPageItem>? = null,
    @JsonProperty("allPopularSeries") val allPopularSeries: DizillaPopularSeries? = null,
    @JsonProperty("getEpisodesOnNewSeries") val getEpisodesOnNewSeries: List<DizillaPageItem>? = null,
    @JsonProperty("getEpisodesOnNewSeason") val getEpisodesOnNewSeason: List<DizillaPageItem>? = null,
    
    // Dizi Detay ve Oynatıcı Verileri
    @JsonProperty("contentItem") val contentItem: DizillaContentItem? = null,
    @JsonProperty("RelatedResults") val relatedResults: DizillaRelatedResults? = null
)

data class DizillaContentItem(
    @JsonProperty("imdb_point") val imdbPoint: Double? = null,
    @JsonProperty("release_year") val releaseYear: Int? = null,
    @JsonProperty("categories") val categories: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("used_long_description") val usedLongDescription: String? = null,
    @JsonProperty("used_short_description") val usedShortDescription: String? = null
)

data class DizillaPopularSeries(
    @JsonProperty("items") val items: List<DizillaPageItem>? = null
)

data class DizillaPageItem(
    @JsonProperty("culture_title") val cultureTitle: String? = null,
    @JsonProperty("original_title") val originalTitle: String? = null,
    @JsonProperty("episode_text") val episodeText: String? = null,
    @JsonProperty("season_text") val seasonText: String? = null,
    @JsonProperty("imdb_point") val imdbPoint: Double? = null,
    @JsonProperty("poster_url") val posterUrl: String? = null,
    @JsonProperty("object_poster_url") val objectPosterUrl: String? = null,
    @JsonProperty("series_poster_url") val seriesPosterUrl: String? = null,
    @JsonProperty("face_url") val faceUrl: String? = null,
    @JsonProperty("brand_url") val brandUrl: String? = null,
    @JsonProperty("square_url") val squareUrl: String? = null,
    @JsonProperty("used_slug") val usedSlug: String? = null,
    @JsonProperty("episode_used_slug") val episodeUsedSlug: String? = null,
    @JsonProperty("object_name") val objectName: String? = null,
    @JsonProperty("series_name") val seriesName: String? = null
)

data class DizillaRelatedResults(
    @JsonProperty("getSerieCastsById") val getSerieCastsById: DizillaResultWrapper<DizillaCast>? = null,
    @JsonProperty("getSerieCreatorsById") val getSerieCreatorsById: DizillaResultWrapper<DizillaCast>? = null,
    @JsonProperty("getSerieSeasonAndEpisodes") val getSerieSeasonAndEpisodes: DizillaResultWrapper<DizillaSeason>? = null,
    @JsonProperty("getEpisodeSources") val getEpisodeSources: DizillaResultWrapper<DizillaSource>? = null,
    @JsonProperty("getSeriesByImdb") val getSeriesByImdb: DizillaResultWrapper<DizillaSeriesByImdb>? = null
)

data class DizillaSeriesByImdb(
    @JsonProperty("imdb_id") val imdbId: String? = null
)

// --- TMDB MODELS ---
data class TmdbFindResponse(
    @JsonProperty("tv_results") val tvResults: List<TmdbTvResult>? = null
)
data class TmdbTvResult(
    @JsonProperty("id") val id: Int? = null
)
data class TmdbSeasonResp(
    @JsonProperty("episodes") val episodes: List<TmdbEpisode>? = null
)
data class TmdbEpisode(
    @JsonProperty("episode_number") val episodeNumber: Int? = null,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("overview") val overview: String? = null,
    @JsonProperty("still_path") val stillPath: String? = null,
    @JsonProperty("runtime") val runtime: Int? = null,
    @JsonProperty("air_date") val airDate: String? = null
)
data class TmdbDetails(
    @JsonProperty("credits") val credits: TmdbCredits? = null,
    @JsonProperty("first_air_date") val firstAirDate: String? = null,
    @JsonProperty("genres") val genres: List<TmdbGenre>? = null
)

data class TmdbGenre(
    @JsonProperty("name") val name: String? = null
)
data class TmdbCredits(
    @JsonProperty("cast") val cast: List<TmdbCast>? = null
)
data class TmdbCast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("character") val character: String? = null,
    @JsonProperty("profile_path") val profilePath: String? = null
)

data class DizillaCast(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("role_name") val roleName: String? = null,
    @JsonProperty("cast_image") val castImage: String? = null
)

data class DizillaResultWrapper<T>(
    @JsonProperty("result") val result: List<T>? = null
)

data class DizillaSeason(
    @JsonProperty("season_no") val seasonNo: Int? = null,
    @JsonProperty("episodes") val episodes: List<DizillaEpisode>? = null
)

data class DizillaEpisode(
    @JsonProperty("episode_no") val episodeNo: Int? = null,
    @JsonProperty("episode_text") val episodeText: String? = null,
    @JsonProperty("episode_description") val episodeDescription: String? = null,
    @JsonProperty("release_date") val releaseDate: String? = null,
    @JsonProperty("used_slug") val usedSlug: String? = null
)

data class DizillaSource(
    @JsonProperty("source_name") val sourceName: String? = null,
    @JsonProperty("language_name") val languageName: String? = null,
    @JsonProperty("quality_name") val qualityName: String? = null,
    @JsonProperty("source_content") val sourceContent: String? = null
)


