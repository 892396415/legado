package io.legado.app.base

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.App
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.CoroutineContext

/**
 * BaseViewModel 作为基础 ViewModel 类，核心价值在于：
 * 提供安全的应用上下文访问（避免内存泄漏）；
 * 封装协程任务调度方法（execute/executeLazy/submit），简化后台任务（如 IO 操作、网络请求）的启动和生命周期管理；
 * 结合 viewModelScope 确保协程随 ViewModel 销毁而取消，避免内存泄漏和无效任务继续执行。
 */

@Suppress("unused")
open class BaseViewModel(application: Application) : AndroidViewModel(application) {

    val context: Context by lazy { this.getApplication<App>() }

    /**
     * 作用：启动一个协程执行后台任务，并返回封装后的 Coroutine<T> 对象，用于管理任务生命周期和结果。
     * 参数说明：
     * scope：协程作用域，默认 viewModelScope（ViewModel 自带的协程作用域，会在 ViewModel 销毁时自动取消所有协程，避免内存泄漏）。
     * context：协程运行的线程上下文，默认 Dispatchers.IO（适合 IO 操作，如网络请求、数据库读写等耗时任务）。
     * start：协程启动模式，默认 CoroutineStart.DEFAULT（立即调度执行）。
     * executeContext：结果回调的线程上下文，默认 Dispatchers.Main（主线程，用于更新 UI）。
     * semaphore：信号量，用于控制并发数量（如限制同时执行的任务数）。
     * block：待执行的挂起函数（后台任务逻辑）。
     * 返回值：Coroutine<T>（应用内部封装的协程管理类，可能用于任务取消、结果监听等）。
     */
    fun <T> execute(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = Dispatchers.IO,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
        block: suspend CoroutineScope.() -> T
    ): Coroutine<T> {
        return Coroutine.async(scope, context, start, executeContext, semaphore, block)
    }

    /**
     * 作用：延迟启动协程任务（懒加载），仅在需要结果时执行，节省资源。
     * 与 execute 的区别：强制指定 start = CoroutineStart.LAZY，即协程不会立即执行，需通过 await() 或 start() 触发。
     * 适用场景：不需要立即执行的任务（如用户主动触发的操作）。
     */
    fun <T> executeLazy(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = Dispatchers.IO,
        executeContext: CoroutineContext = Dispatchers.Main,
        semaphore: Semaphore? = null,
        block: suspend CoroutineScope.() -> T
    ): Coroutine<T> {
        return Coroutine.async(
            scope, context, CoroutineStart.LAZY, executeContext, semaphore, block
        )
    }

    /**
     * 作用：提交一个返回 Deferred<R> 的任务，并等待其完成后返回结果。
     * 参数说明：
     * block：返回 Deferred<R> 的挂起函数（Deferred 是带结果的 Job，可通过 await() 获取结果）。
     * 逻辑：通过 block().await() 等待 Deferred 任务完成，将结果包装成 Coroutine<R> 返回，适合需要串联多个异步任务的场景。
     */
    fun <R> submit(
        scope: CoroutineScope = viewModelScope,
        context: CoroutineContext = Dispatchers.IO,
        block: suspend CoroutineScope.() -> Deferred<R>
    ): Coroutine<R> {
        return Coroutine.async(scope, context) { block().await() }
    }

}