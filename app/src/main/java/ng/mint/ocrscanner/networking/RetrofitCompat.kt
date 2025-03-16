package ng.mint.ocrscanner.networking

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitCompat {
    // Retry configuration
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_DELAY = 2000L // 2 seconds
    
    // Timeouts
    private const val CONNECTION_TIMEOUT = 30L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    // API URLs
    private const val BASE_URL = "https://lookup.binlist.net/"
    
    fun getInstance(token: String = "false"): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val retryInterceptor = Interceptor { chain ->
            var retryCount = 0
            var response = chain.proceed(chain.request())
            
            while (!response.isSuccessful && retryCount < MAX_RETRIES) {
                when (response.code) {
                    429 -> { // Too Many Requests
                        // Get retry-after header or use exponential backoff
                        val retryAfter = response.header("Retry-After")?.toLongOrNull() 
                            ?: (INITIAL_BACKOFF_DELAY * (1 shl retryCount))
                        Thread.sleep(retryAfter)
                    }
                    in 500..599 -> { // Server errors
                        Thread.sleep(INITIAL_BACKOFF_DELAY * (1 shl retryCount))
                    }
                    else -> break // Don't retry other error codes
                }
                
                response.close()
                retryCount++
                response = chain.proceed(chain.request())
            }
            
            response
        }

        val okHttpBuilder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(retryInterceptor)

        if (token != "false") {
            okHttpBuilder.addInterceptor { chain: Interceptor.Chain ->
                val request = chain.request()
                val newRequest = request.newBuilder().header("Authorization", "Bearer $token")
                chain.proceed(newRequest.build())
            }
        }

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpBuilder.build())
            .build()
    }
}