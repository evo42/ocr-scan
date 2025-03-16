package ng.mint.ocrscanner.networking

import ng.mint.ocrscanner.model.CardResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiInterface {
    @GET("api/lookup/single")
    suspend fun getCardDetail(@Query("bin") cardPan: String): Response<CardResponse>
}
