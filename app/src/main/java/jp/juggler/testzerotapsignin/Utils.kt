package jp.juggler.testzerotapsignin

import org.koin.core.Koin
import org.koin.core.context.GlobalContext

val koin : Koin
    get() = GlobalContext.get()


