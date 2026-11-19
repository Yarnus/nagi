package dev.nagi.app

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
import coil.EventListener
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.network.HttpException
import coil.request.ErrorResult
import coil.request.ImageRequest
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class NagiApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
        .eventListenerFactory {
            object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    val host = (request.data as? String)?.let { Uri.parse(it).host }.orEmpty()
                    val code = (result.throwable as? HttpException)?.response?.code
                    val detail = if (code != null) "HTTP $code" else result.throwable::class.java.simpleName
                    Log.w("NagiImage", "Image load failed ($host): $detail")
                }
            }
        }
        .components {
            if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()
}
