package net.hordecraft.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class AsyncRequestResponseDto(
    val id: String,
    val kudos: Double = 0.0,
    val message: String? = null,
    val warnings: List<RequestWarningDto> = emptyList(),
)

@Serializable
data class RequestWarningDto(
    val code: String = "",
    val message: String = "",
)

@Serializable
data class GenerationCheckDto(
    val finished: Int = 0,
    val processing: Int = 0,
    val restarted: Int = 0,
    val waiting: Int = 0,
    val done: Boolean = false,
    val faulted: Boolean = false,
    @SerialName("wait_time") val waitTime: Int = 0,
    @SerialName("queue_position") val queuePosition: Int = 0,
    val kudos: Double = 0.0,
    @SerialName("is_possible") val isPossible: Boolean = true,
)

@Serializable
data class GenerationStatusDto(
    val finished: Int = 0,
    val processing: Int = 0,
    val restarted: Int = 0,
    val waiting: Int = 0,
    val done: Boolean = false,
    val faulted: Boolean = false,
    @SerialName("wait_time") val waitTime: Int = 0,
    @SerialName("queue_position") val queuePosition: Int = 0,
    val kudos: Double = 0.0,
    @SerialName("is_possible") val isPossible: Boolean = true,
    val generations: List<GeneratedImageDto> = emptyList(),
)

@Serializable
data class GeneratedImageDto(
    val id: String = "",
    val img: String = "",
    val seed: String = "",
    val model: String = "",
    @SerialName("worker_id") val workerId: String = "",
    @SerialName("worker_name") val workerName: String = "",
    val censored: Boolean = false,
    @SerialName("gen_metadata") val genMetadata: List<JsonElement> = emptyList(),
)

@Serializable
data class ActiveModelDto(
    val name: String = "",
    val count: Int = 0,
    val queued: Double = 0.0,
    val eta: Int = 0,
    val performance: Double = 0.0,
    val type: String = "image",
)

@Serializable
data class HordeStyleDto(
    val id: String = "",
    val name: String = "",
    val prompt: String = "{p}###{np}",
    val models: List<String> = emptyList(),
    @SerialName("shared_key") val sharedKey: String? = null,
    val params: JsonObject? = null,
)

@Serializable
data class HordeUserDto(
    val id: Long = 0,
    val username: String = "",
    val kudos: Double = 0.0,
    val trusted: Boolean = false,
    @SerialName("worker_count") val workerCount: Int = 0,
    val records: UserRecordsDto? = null,
)

@Serializable
data class UserRecordsDto(
    val usage: UserUsageDto? = null,
    val contribution: UserContributionDto? = null,
)

@Serializable
data class UserUsageDto(
    val megapixelsteps: Double = 0.0,
    val requests: Long = 0,
)

@Serializable
data class UserContributionDto(
    val megapixelsteps: Double = 0.0,
    val fulfillments: Long = 0,
)

@Serializable
data class SharedKeyDto(
    val id: String = "",
    val name: String = "",
    val kudos: Long = 0,
    @SerialName("utilized") val utilized: Long = 0,
    @SerialName("max_image_pixels") val maxImagePixels: Long = 0,
    @SerialName("max_image_steps") val maxImageSteps: Int = 0,
    val expiry: String? = null,
)

interface HordeApiService {
    @POST("generate/async")
    suspend fun submitAsync(@Body payload: JsonObject): AsyncRequestResponseDto

    @GET("generate/check/{id}")
    suspend fun checkGeneration(@Path("id") id: String): GenerationCheckDto

    @GET("generate/status/{id}")
    suspend fun getGenerationStatus(@Path("id") id: String): GenerationStatusDto

    @DELETE("generate/status/{id}")
    suspend fun cancelGeneration(@Path("id") id: String): GenerationStatusDto

    @GET("status/models")
    suspend fun getModels(@Query("type") type: String = "image"): List<ActiveModelDto>

    @GET("styles/image")
    suspend fun getImageStyles(@Query("page") page: Int = 1): List<HordeStyleDto>

    @GET("find_user")
    suspend fun findUser(): HordeUserDto

    @GET("sharedkeys/{id}")
    suspend fun getSharedKey(@Path("id") id: String): SharedKeyDto
}
