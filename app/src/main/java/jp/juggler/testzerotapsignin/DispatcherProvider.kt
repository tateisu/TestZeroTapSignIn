package jp.juggler.testzerotapsignin

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers.IO の DI抽象化
 */
interface DispatcherProvider{
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val mainImmediate: CoroutineDispatcher
}

class DispatcherProviderAndroid: DispatcherProvider {
    override val io = Dispatchers.IO
    override val main = Dispatchers.Main
    override val mainImmediate = Dispatchers.Main.immediate
}
