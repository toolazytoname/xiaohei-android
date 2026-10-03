package com.ai.assistance.operit.api.chat.llmprovider

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import org.junit.Assert.assertSame
import org.junit.Test

class XiaoheiModelTlsTest {
    @Test fun commonProfilesKeepVerifiedClientAndPins() {
        val pin = CertificatePinner.Builder()
            .add("example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
        val original = OkHttpClient.Builder().certificatePinner(pin).build()
        val builder = original.newBuilder()
        assertSame(builder, UnsafeModelSsl.apply(builder, commonBase = true))
        val protected = builder.build()
        assertSame(original.sslSocketFactory, protected.sslSocketFactory)
        assertSame(original.hostnameVerifier, protected.hostnameVerifier)
        assertSame(original.certificatePinner, protected.certificatePinner)
    }
}
