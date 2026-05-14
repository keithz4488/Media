package com.kzaller.shelf.data.api

import com.kzaller.shelf.data.models.CreateItemRequest
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

    @PATCH("items/{id}")
    suspend fun update(@Path("id") id: String, @Body body: UpdateItemRequest): ItemResponse

    @DELETE("items/{id}")
    suspend fun delete(@Path("id") id: String)

    @GET("search/books")
    suspend fun searchBooks(@Query("q") q: String? = null, @Query("isbn") isbn: String? = null): SearchResponse

    @GET("search/movies")
    suspend fun searchMovies(@Query("q") q: String? = null, @Query("id") tmdbId: String? = null): SearchResponse

    @GET("search/tv")
    suspend fun searchTv(@Query("q") q: String? = null, @Query("id") tmdbId: String? = null): SearchResponse

    @GET("search/games")
    suspend fun searchGames(@Query("q") q: String? = null, @Query("slug") slug: String? = null): SearchResponse
}
