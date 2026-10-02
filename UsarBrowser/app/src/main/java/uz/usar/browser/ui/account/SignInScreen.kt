package uz.usar.browser.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.usar.browser.AppContainer
import uz.usar.browser.R
import uz.usar.browser.auth.GoogleAuth
import uz.usar.browser.auth.SignInResult
import uz.usar.browser.data.settings.AccountMode
import uz.usar.browser.ui.browser.findActivity

/** Lets the user sign in with Google or continue as a guest. Shown after onboarding and from Settings. */
@Composable
fun SignInScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var noAccount by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(104.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.signin_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.signin_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    val activity = context.findActivity() ?: return@Button
                    loading = true
                    error = null
                    noAccount = false
                    scope.launch {
                        when (val result = container.auth.signIn(activity)) {
                            is SignInResult.Success -> {
                                container.settings.setAccount(AccountMode.GOOGLE, result.name, result.email)
                                loading = false
                                onDone()
                                return@launch
                            }
                            SignInResult.Cancelled -> Unit
                            SignInResult.NoAccount -> {
                                noAccount = true
                                error = R.string.signin_no_account
                            }
                            SignInResult.NotConfigured -> error = R.string.signin_not_configured
                            SignInResult.Failed -> error = R.string.signin_failed
                        }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                }
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.signin_google))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        container.settings.setAccount(AccountMode.GUEST)
                        onDone()
                    }
                },
                enabled = !loading,
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.Filled.PersonOutline, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.signin_guest))
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { GoogleAuth.openCreateAccount(context) }) {
                Text(stringResource(if (noAccount) R.string.signin_create_account_now else R.string.signin_create_account))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.signin_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
    }
}
