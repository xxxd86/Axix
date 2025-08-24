package com.redwolf.mvi_dynamic_plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface AxMviAction
interface AxMviState
interface AxMviEffect

object NoEffect : AxMviEffect   // ← 新增：通用占位 Effect


fun interface Reducer<S: AxMviState, A: AxMviAction, E: AxMviEffect> { fun reduce(state: S, action: A): Pair<S, E?> }


typealias Middleware<A> = (actions: Flow<A>) -> Flow<A>


class Store<A: AxMviAction, S: AxMviState, E: AxMviEffect>(
    initialState: S,
    private val reducer: Reducer<S, A, E>,
    private val middlewares: List<Middleware<A>> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()


    private val _effect = MutableSharedFlow<E>(replay=0, extraBufferCapacity=64)
    val effect: SharedFlow<E> = _effect.asSharedFlow()


    private val input = MutableSharedFlow<A>(extraBufferCapacity=64)
    private val _actions = MutableSharedFlow<A>(extraBufferCapacity=64)
    val actions: SharedFlow<A> = _actions.asSharedFlow()


    private var job: Job? = null


    fun start() {
        if (job != null) return
        val pipeline = middlewares.fold(input as Flow<A>) { acc, m -> m(acc) }
        job = scope.launch {
            pipeline.onEach { _actions.emit(it) }.collect { action ->
                val (ns, eff) = reducer.reduce(_state.value, action)
                _state.value = ns
                eff?.let { _effect.emit(it) }
            }
        }
    }


    fun stop() { job?.cancel(); job = null }
    fun dispatch(a: A) { input.tryEmit(a) }
}