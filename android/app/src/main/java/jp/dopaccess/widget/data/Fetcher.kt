package jp.dopaccess.widget.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gets a token silently and reads the realtime numbers. Used by the background refresh and the settings screen. */
object Fetcher {
    sealed interface Result {
        data class Ok(val reading: Reading) : Result
        data class Fail(val message: String) : Result
    }

    suspend fun fetch(ctx: Context, propertyId: String): Result {
        if (propertyId.isBlank()) return Result.Fail("サイト未選択")
        repeat(2) { attempt ->
            val token = when (val t = GoogleAuth.token(ctx)) {
                is GoogleAuth.Token.Ok -> t.accessToken
                is GoogleAuth.Token.NeedsConsent -> return Result.Fail("要ログイン")
                is GoogleAuth.Token.Fail -> return Result.Fail("ログイン失敗")
            }
            try {
                return Result.Ok(Ga4(token).realtime(propertyId))
            } catch (e: Ga4.HttpError) {
                // An expired token: drop it and try once more with a fresh one.
                if (e.code == 401 && attempt == 0) withContext(Dispatchers.IO) { GoogleAuth.invalidate(ctx, token) }
                else return Result.Fail(if (e.code == 403) "権限なし" else "HTTP ${e.code}")
            } catch (e: Exception) {
                return Result.Fail("通信エラー")
            }
        }
        return Result.Fail("ログイン失敗")
    }
}
