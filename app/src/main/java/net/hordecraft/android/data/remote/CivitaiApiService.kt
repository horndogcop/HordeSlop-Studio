package net.hordeslop.studio.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class CivitaiSearchResponseDto(
    val items: List<CivitaiModelDto> = emptyList(),
)

@Serializable
data class CivitaiModelDto(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val nsfw: Boolean = false,
    val modelVersions: List<CivitaiModelVersionDto> = emptyList(),
)

@Serializable
data class CivitaiModelVersionDto(
    val id: Long = 0,
    val modelId: Long? = null,
    val name: String = "",
    val baseModel: String = "SDXL 1.0",
    val trainedWords: List<String> = emptyList(),
    val images: List<CivitaiImageDto> = emptyList(),
    val model: CivitaiVersionParentModelDto? = null,
)

@Serializable
data class CivitaiVersionParentModelDto(
    val name: String? = null,
    val type: String? = null,
    val nsfw: Boolean? = null,
)

@Serializable
data class CivitaiImageDto(
    val url: String = "",
    val nsfwLevel: Int? = null,
    val nsfw: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class CivitaiImagesResponseDto(
    val items: List<CivitaiImageItemDto> = emptyList(),
)

@Serializable
data class CivitaiImageItemDto(
    val id: Long = 0,
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val meta: JsonElement? = null,
)

interface CivitaiApiService {
    @GET("api/v1/models")
    suspend fun searchModels(
        @Header("Authorization") authHeader: String?,
        @Query("query") query: String?,
        @Query("types") type: String,
        @Query("baseModels") baseModels: List<String>?,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "Most Downloaded",
        @Query("nsfw") nsfw: Boolean = true,
    ): CivitaiSearchResponseDto

    @GET("api/v1/models/{id}")
    suspend fun getModelById(
        @Header("Authorization") authHeader: String?,
        @Path("id") id: Long,
    ): CivitaiModelDto

    @GET("api/v1/model-versions/{id}")
    suspend fun getModelVersionById(
        @Header("Authorization") authHeader: String?,
        @Path("id") id: Long,
    ): CivitaiModelVersionDto

    @GET("api/v1/images")
    suspend fun getImageById(
        @Header("Authorization") authHeader: String?,
        @Query("imageId") imageId: Long,
    ): CivitaiImagesResponseDto
}
