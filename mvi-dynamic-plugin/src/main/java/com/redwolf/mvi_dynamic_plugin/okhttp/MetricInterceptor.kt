package com.redwolf.mvi_dynamic_plugin.okhttp

import okhttp3.Interceptor
import okhttp3.Response

class MetricInterceptor(private val onMetric: (path:String, bytes:Int, ms:Long)->Unit): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val t0 = System.nanoTime()
        val res = chain.proceed(chain.request())
        val t1 = System.nanoTime()
        val bytes = (res.body?.contentLength() ?: 0L).toInt().coerceAtLeast(0)
        onMetric(chain.request().url.encodedPath, bytes, (t1 - t0) / 1_000_000)
        return res
    }
}