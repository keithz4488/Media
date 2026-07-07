package com.kzaller.shelf.data.api

import com.kzaller.shelf.data.models.BulkCreateRequest
import com.kzaller.shelf.data.models.BulkCreateResponse
import com.kzaller.shelf.data.models.CoversResponse
import com.kzaller.shelf.data.models.CreateItemRequest
import com.kzaller.shelf.data.models.IdentifyRequest
import com.kzaller.shelf.data.models.IdentifyResponse
import com.kzaller.shelf.data.models.ItemResponse
import com.kzaller.shelf.data.models.ItemsResponse
import com.kzaller.shelf.data.models.SearchResponse
import com.kzaller.shelf.data.models.UpdateItemRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ShelfApi {
    @GET("items")
    suspend fun list(@Query("kind") kind: String? = null): ItemsResponse

    @POST("items")
    suspend fun create(@Body body: CreateItemRequest): ItemResponse

    @POST("items/bulk")
    suspend fun bulkCreate(@Body body: BulkCreateRequest): BulkCreateResponse

    @PATCH("items/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateItemRequest): ItemResponse

    @DELETE("items/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("items/{id}/refresh")
    suspend fun refresh(@Path("id") id: String): ItemResponse

    @GET("items/{id}/covers")
    suspend fun covers(@Path("id") id: String): CoversResponse

    @GET("items/{id}/scores")
    suspend fun scores(@Path("id") id: String): com.kzaller.shelf.data.models.ScoresResponse

    @GET("search/books")
    suspend fun searchBooks(@Query("q") q: String? = null, @Query("isbn") isbn: String? = null): SearchResponse

    @GET("search/movies")
    suspend fun searchMovies(@Query("q") q: String? = null, @Query("id") tmdbId: String? = null): SearchResponse

    @GET("search/tv")
    suspend fun searchTv(@Query("q") q: String? = null, @Query("id") tmdbId: String? = null): SearchResponse

    @GET("search/games")
    suspend fun searchGames(@Query("q") q: String? = null, @Query("slug") slug: String? = null): SearchResponse

    @POST("identify")
    suspend fun identify(@Body body: IdentifyRequest): IdentifyResponse
}
