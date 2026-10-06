package jp.juggler.testzerotapsignin

import android.app.Application
import androidx.credentials.CredentialManager
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App1 : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@App1)
            modules(
                module {
                    single { OkHttpClient() }
                    single { CredentialManager.create(androidContext()) }
                    single<DispatcherProvider> { DispatcherProviderAndroid() }
                    single {
                        RestoreCredentialsRepository(
                            context = androidContext(),
                            credentialManager = get(),
                            httpClient = get(),
                            dispatcherProvider = get(),
                        )
                    }
                    viewModel<MainActivityViewModel> {
                        MainActivityViewModelImpl(
                            dispatcherProvider = get(),
                            repository = get(),
                        )
                    }
                }
            )
        }
    }
}
