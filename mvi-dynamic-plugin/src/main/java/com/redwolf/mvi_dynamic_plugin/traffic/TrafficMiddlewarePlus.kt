package com.redwolf.mvi_dynamic_plugin.traffic

import com.redwolf.mvi_dynamic_plugin.Middleware
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit


/**
 * 在基础限流/合并的基础上，接入端侧智能（EdgeAdvisor）与字节预算（ByteBudgetManager）。
 * 为保持易运行，动态并发不在运行期调整（需要更复杂的可变信号量实现）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrafficMiddlewarePlus<A: Any>(
    private val configProvider: (key: String) -> TrafficConfig = { TrafficConfig() },
    private val mapKey: (A) -> String?,
    private val coalesceKey: (A) -> String? = mapKey,
    private val estimateBytes: (A) -> Int = { 8 * 1024 },
    private val edgeAdvisor: EdgeAdvisor? = null,
    private val signalsProvider: (() -> EdgeSignals)? = null,
    private val budgetManager: ByteBudgetManager? = null,
) : Middleware<A> {


    private data class Bucket(val limiter: TokenBucket, val semaphore: Semaphore)
    private val buckets = mutableMapOf<String, Bucket>()
    private fun bucketFor(key: String): Bucket = buckets.getOrPut(key) {
        val cfg = configProvider(key)
        Bucket(TokenBucket(cfg.tokenCapacity, cfg.tokensPerSecond), Semaphore(cfg.maxConcurrent))
    }


    override fun invoke(actions: Flow<A>): Flow<A> = actions
        .transformLatest { emit(it) } // 合并同 key 高频动作（coalesce 语义由上层控制）
        .flatMapMerge(concurrency = 64) { a ->
            val key = mapKey(a)
            if (key == null) return@flatMapMerge flowOf(a)
            val b = bucketFor(key)
            val cfg = configProvider(key)
            flow {
// 端侧决策 + 预算 gating
                val allowByEdge = edgeAdvisor?.let { adv ->
                    val s = signalsProvider?.invoke()
                    if (s != null) adv.decide(key, a::class.simpleName ?: "action", s).allow else true
                } ?: true
                if (!allowByEdge) return@flow


                val canToken = b.limiter.tryConsume(1)
                if (!canToken) {
                    when (cfg.dropStrategy) {
                        DropStrategy.DROP_LATEST -> return@flow
                        DropStrategy.DROP_OLDEST, DropStrategy.COALESCE -> emit(a)
                    }
                } else {
// 预算
                    val okBudget = budgetManager?.let { it.allow(key, estimateBytes(a), (edgeAdvisor as? HeuristicEdgeAdvisor)?.let { _ ->
// 简化：再次调用以拿 budget（或由上层传入）
                        2048
                    } ?: 2048) } ?: true
                    if (!okBudget) return@flow
                    b.semaphore.withPermit { emit(a) }
                }
            }
        }
}