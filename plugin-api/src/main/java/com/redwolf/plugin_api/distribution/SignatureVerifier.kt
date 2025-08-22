package com.redwolf.plugin_api.distribution

import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec


object SignatureVerifier {
    // 你的公钥（x509/DER，Base64），可放多把（kid → 公钥）
    private val pubKeys: Map<String, String> = mapOf(
        "reg-2025-keyA" to "MIIBIjANBgkq..."
    )


    private val jsonCanonical =
        Json { prettyPrint = false; encodeDefaults = true; explicitNulls = false }


    /** 对 modules.json 的 sign 进行验签（可选）。 */
    fun verifyOrNull(body: ModulesJson): Boolean? {
        val s = body.sign ?: return null
        val keyB64 = pubKeys[s.kid] ?: return false
        val pub = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.decode(keyB64, Base64.DEFAULT))
        )
        val sig = Signature.getInstance(
            when (s.alg.uppercase()) {
                "RSA-PSS-2048-SHA256", "PS256" -> "RSASSA-PSS"
                "RSA-SHA256", "RS256" -> "SHA256withRSA"
                else -> "SHA256withRSA"
            }
        )
        if (sig.algorithm == "RSASSA-PSS") {
            sig.setParameter(java.security.spec.PSSParameterSpec.DEFAULT)
        }
        sig.initVerify(pub)
        val canonicalModules = jsonCanonical.encodeToString(body.modules).toByteArray()
        sig.update(canonicalModules)
        val sigBytes = Base64.decode(s.sig, Base64.DEFAULT)
        return sig.verify(sigBytes)
    }
}