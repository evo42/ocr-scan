package ng.mint.ocrscanner.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CardResponse(
    @Json(name = "bin")
    var bin: String? = null,

    @Json(name = "vendor")
    var scheme: String? = null,

    @Json(name = "type")
    var type: String? = null,

    @Json(name = "level")
    var category: String? = null,

    @Json(name = "bank")
    var bank: Bank? = null,

    @Json(name = "country")
    var country: Country? = null,

    @Json(name = "success")
    var success: Boolean = true,

    @Json(name = "error")
    var reason: String? = null
) {

    @JsonClass(generateAdapter = true)
    data class Country(
        @Json(name = "name")
        var name: String? = null,

        @Json(name = "code")
        var alpha2: String? = null,

        @Json(name = "currency")
        var currency: String? = null,

        // We'll keep the emoji field even though it's not in the API response
        // to maintain compatibility with the existing UI
        var emoji: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class Bank(
        @Json(name = "name")
        var name: String? = null,

        @Json(name = "website")
        var url: String? = null,

        @Json(name = "phone")
        var phone: String? = null
    )
}