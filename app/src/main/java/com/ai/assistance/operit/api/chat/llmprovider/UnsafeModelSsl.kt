package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.core.commonbase.CommonBaseProfile
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * 为模型请求统一关闭 HTTPS 证书链与主机名校验。
 * 小黑两版保留 OkHttp/platform 的证书与主机名验证；仅未分发的上游配置保留原行为。
 */
internal object UnsafeModelSsl {
    fun apply(
        builder: OkHttpClient.Builder,
        commonBase: Boolean = CommonBaseProfile.isEnabled
    ): OkHttpClient.Builder {
        // Root/side-load is not a reason to trust arbitrary TLS peers. Preserve the supplied
        // verified client, including its certificate pinner, in BOTH Xiaohei variants.
        if (commonBase) return builder
        val trustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {}

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?
                ) {}

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    }
}
