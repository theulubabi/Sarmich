package uz.usar.browser.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import uz.usar.browser.BuildConfig

sealed interface SignInResult {
    data class Success(val name: String, val email: String) : SignInResult
    data object Cancelled : SignInResult
    data object NoAccount : SignInResult
    data object NotConfigured : SignInResult
    data object Failed : SignInResult
}

/**
 * "Sign in with Google" through Android Credential Manager. The account picker and any password entry
 * are shown by Google Play services; Usar never sees or stores passwords or ID tokens.
 */
class GoogleAuth(context: Context) {
    private val credentialManager = CredentialManager.create(context)

    val isConfigured: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    suspend fun signIn(activity: Activity): SignInResult {
        if (!isConfigured) return SignInResult.NotConfigured
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                SignInResult.Success(name = google.displayName.orEmpty(), email = google.id)
            } else {
                SignInResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            SignInResult.NoAccount
        } catch (e: GoogleIdTokenParsingException) {
            SignInResult.Failed
        } catch (e: GetCredentialException) {
            SignInResult.Failed
        }
    }

    suspend fun signOut() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    companion object {
        /** Opens Android's own "Add account" screen for Google, where a new Google account can be created. */
        fun openCreateAccount(context: Context) {
            val addAccount = Intent(Settings.ACTION_ADD_ACCOUNT)
                .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(addAccount) }.isSuccess
            if (!ok) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com/signup"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }
}
