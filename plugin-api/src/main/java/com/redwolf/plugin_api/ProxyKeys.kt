package com.redwolf.plugin_api

object ProxyKeys {
    const val HOST_NAME = "__host__"
    const val EXTRA_MODULE_NAME = "module_name"
    const val EXTRA_TARGET_CLASS = "target_activity"
    const val EXTRA_VERSION = "module_version"
    const val EXTRA_REMOTE_URL = "remote_url"
    const val EXTRA_SHA256 = "sha256"
    const val EXTRA_CERT_SHA256 = "cert_sha256"
    const val EXTRA_STRATEGY = "load_strategy"
    const val EXTRA_THEME_RES_ID = "theme_res_id"
    const val EXTRA_USE_FRAGMENT_FACTORY = "use_fragment_factory"
    const val EXTRA_NETWORK_POLICY = "network_policy"

    const val HOST_LOCAL_MODULE = "__host__"
    const val EXTRA_COMPONENT_KIND = "component_kind" // "activity" | "service" | "receiver"
    const val HOST_PLUGIN_PROXY_ACTIVITY = "com.redwolf.plugin_api.core.PluginProxyActivity"
}