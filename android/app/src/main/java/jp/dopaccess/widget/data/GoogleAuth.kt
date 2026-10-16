package jp.dopaccess.widget.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Google sign-in for GA4 read access. Google Play services keeps the grant and hands out
 * short-lived access tokens; this app never stores a token or password itself.
 */
object GoogleAuth {
    private const val SCOPE = "https://www.googleapis.com/auth/analytics.readonly"

    sealed interface Token {
        data class Ok(val accessToken: String) : Token
        /** The user has to approve once (or again) on screen. */
        data class NeedsConsent(val intent: PendingIntent) : Token
        data class Fail(val message: String) : Token
    }

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    /** Silent when already granted; otherwise returns the consent screen to launch. */
    suspend fun token(ctx: Context): Token = try {
        toToken(Identity.getAuthorizationClient(ctx).authorize(request()).await())
    } catch (e: Exception) {
        Token.Fail(e.javaClass.simpleName + ": " + (e.message ?: ""))
    }

    /** Reads the result after the consent screen returns. */
    fun fromIntent(ctx: Context, data: Intent?): Token = try {
        toToken(Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data))
    } catch (e: Exception) {
        Token.Fail(e.javaClass.simpleName + ": " + (e.message ?: ""))
    }

    private fun toToken(r: AuthorizationResult): Token {
        val pi = r.pendingIntent
        return when {
            r.hasResolution() && pi != null -> Token.NeedsConsent(pi)
            r.accessToken != null -> Token.Ok(r.accessToken!!)
            else -> Token.Fail("no access token")
        }
    }

    /** Forget a token the API rejected, so the next call fetches a fresh one. */
    suspend fun invalidate(ctx: Context, token: String) {
        runCatching {
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(ctx, token)
        }
    }
}
