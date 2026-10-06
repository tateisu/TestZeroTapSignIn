package jp.juggler.testzerotapsignin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainActivityContent(viewModel: MainActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    App1Theme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                Text("Restore Credentials デモ", style = MaterialTheme.typography.headlineSmall)
                Text(uiState.text)
                for ((onClick, text) in arrayOf(
                    viewModel::demoSignInAndRegister to "デモログインして Restore Credential を登録",
                    viewModel::restoreSignIn to "Restore Credential でサインイン",
                    viewModel::signOut to "ログアウトして Restore Credential を削除"
                )) {
                    Button(
                        enabled = !uiState.isLoading,
                        onClick = onClick,
                    ) {
                        Text(text)
                    }
                }
            }
        }
    }
}
