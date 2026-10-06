package jp.juggler.testzerotapsignin

import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

abstract class MainActivityViewModel : ViewModel() {
    companion object {
        val initialUiState = UiState(false, "起動しています…")
    }

    data class UiState(
        val isLoading: Boolean = false,
        val text: String = "起動しています…",
    )

    val uiState = MutableStateFlow(initialUiState)

    abstract fun loadOrRestore()
    abstract fun demoSignInAndRegister()
    abstract fun restoreSignIn()
    abstract fun signOut()
}

class MainActivityViewModelImpl(
    private val dispatcherProvider: DispatcherProvider,
    private val repository: RestoreCredentialsRepository,
) : MainActivityViewModel() {
    private fun errorMessage(error: Exception): String = when (error) {
        is NoCredentialException ->
            "復元できる資格情報がありません。デモログインから登録してください。"

        else -> "処理エラー: ${error.message}"
    }

    private fun runAction(initialStatus: String, action: suspend () -> String) {
        if (uiState.value.isLoading) return
        uiState.value = UiState(isLoading = true, text = initialStatus)
        viewModelScope.launch(dispatcherProvider.io) {
            uiState.value = UiState(
                isLoading = false,
                text = try {
                    action()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage(error)
                },
            )
        }
    }

    override fun loadOrRestore() = runAction(
        "Restore Credential を確認中…"
    ) {
        if (repository.hasSession()) {
            "デモアカウントでログイン中"
        } else {
            val user = repository.signInWithRestoreCredential()
            "Restore Credential で $user としてログインしました。"
        }
    }

    override fun demoSignInAndRegister() = runAction(
        "デモログイン中…"
    ) {
        repository.signIn()
        repository.registerDemoRestoreCredential()
        "デモログイン済み。Restore Credential を登録しました。"
    }

    override fun restoreSignIn() = runAction(
        "Restore Credential を確認中…"
    ) {
        val user = repository.signInWithRestoreCredential()
        "Restore Credential で $user としてログインしました。"
    }

    override fun signOut() = runAction(
        "ログアウト中…"
    ) {
        repository.signOut()
        "ログアウトしました。Restore Credential も削除しました。"
    }
}
