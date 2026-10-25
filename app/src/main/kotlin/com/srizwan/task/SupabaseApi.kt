package com.srizwan.task

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ---------------------------------------------------------------------------
// Config (values come from local.properties -> BuildConfig, see app/build.gradle.kts)
// ---------------------------------------------------------------------------
object AppConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** Admin mobile number, e.g. 01714656343 (from local.properties ADMIN_PHONE). */
    val adminPhone: String = BuildConfig.ADMIN_PHONE.trim().ifBlank { "01714656343" }

    /** wa.me link for the admin, e.g. https://wa.me/8801714656343?text=... */
    fun whatsappUrl(message: String): String {
        val digits = adminPhone.filter { it.isDigit() }
        val international = when {
            digits.startsWith("880") -> digits
            digits.startsWith("0") -> "88$digits"
            else -> "880$digits"
        }
        val text = URLEncoder.encode(message, "UTF-8")
        return "https://wa.me/$international?text=$text"
    }
}

// ---------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------
data class AppUser(
    val id: String,
    val fullName: String,
    val address: String,
    val phone: String,
    val payMethod: String,   // "bkash" or "nagad"
    val payNumber: String,
    val createdAt: String = "",      // ISO time, used to hide notices older than the account
    val isAdminFlag: Boolean = false // sent by the server (admin phone)
) {
    /** Admin = server says so, or the number equals ADMIN_PHONE from local.properties. */
    val isAdmin: Boolean
        get() = isAdminFlag || (phone.isNotBlank() && samePhone(phone, AppConfig.adminPhone))

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("full_name", fullName)
        .put("address", address)
        .put("phone", phone)
        .put("pay_method", payMethod)
        .put("pay_number", payNumber)
        .put("created_at", createdAt)
        .put("is_admin", isAdminFlag)

    companion object {
        fun fromJson(j: JSONObject) = AppUser(
            id = j.optString("id"),
            fullName = j.optString("full_name"),
            address = j.optString("address"),
            phone = j.optString("phone"),
            payMethod = j.optString("pay_method"),
            payNumber = j.optString("pay_number"),
            createdAt = j.optString("created_at"),
            isAdminFlag = j.optBoolean("is_admin", false)
        )
    }
}

enum class AuthError {
    INVALID_PHONE, INVALID_PIN, INVALID_NAME, INVALID_ADDRESS, INVALID_PAY_NUMBER,
    USER_NOT_FOUND, WRONG_PIN, LOCKED, BLOCKED,
    DEVICE_MISMATCH, DEVICE_IN_USE, PHONE_EXISTS, INVALID_SESSION,
    NETWORK, NOT_CONFIGURED, SERVER_SETUP, UNKNOWN
}

sealed interface ApiResult {
    data class Success(val user: AppUser, val token: String?) : ApiResult

    /** [extra]: WRONG_PIN -> attempts left, LOCKED -> seconds left */
    data class Failure(val error: AuthError, val extra: Int = 0) : ApiResult
}

/** Result of [SupabaseApi.call]. [Fail.error] = "network", "not_configured", "unknown" or a server code. */
sealed interface CallResult {
    data class Ok(val json: JSONObject) : CallResult
    data class Fail(val error: String, val value: Double = 0.0) : CallResult
}

// ---------------------------------------------------------------------------
// Supabase REST (PostgREST RPC) - no extra library, only HttpURLConnection
// ---------------------------------------------------------------------------
object SupabaseApi {

    suspend fun login(phone: String, pin: String, androidId: String): ApiResult =
        rpc(
            "login_user",
            JSONObject()
                .put("p_phone", phone)
                .put("p_pin", pin)
                .put("p_android_id", androidId)
        )

    suspend fun register(
        fullName: String,
        address: String,
        phone: String,
        payMethod: String,
        payNumber: String,
        pin: String,
        androidId: String
    ): ApiResult =
        rpc(
            "register_user",
            JSONObject()
                .put("p_full_name", fullName)
                .put("p_address", address)
                .put("p_phone", phone)
                .put("p_pay_method", payMethod)
                .put("p_pay_number", payNumber)
                .put("p_pin", pin)
                .put("p_android_id", androidId)
        )

    suspend fun checkSession(token: String, androidId: String): ApiResult =
        rpc(
            "check_session",
            JSONObject()
                .put("p_token", token)
                .put("p_android_id", androidId)
        )

    suspend fun logout(token: String) {
        rpc("logout_user", JSONObject().put("p_token", token))
    }

    /**
     * Generic RPC for the Task / Profile / Admin pages (everything except login & signup).
     * The server answers {ok:true, ...} or {ok:false, error:"...", value:n}.
     */
    suspend fun call(function: String, body: JSONObject = JSONObject()): CallResult =
        when (val raw = post(function, body)) {
            is Raw.Error -> CallResult.Fail(
                when (raw.error) {
                    AuthError.NETWORK -> "network"
                    AuthError.NOT_CONFIGURED -> "not_configured"
                    AuthError.SERVER_SETUP -> "setup_missing"
                    else -> "unknown"
                }
            )
            is Raw.Body -> try {
                val json = JSONObject(raw.text)
                if (json.optBoolean("ok", false)) CallResult.Ok(json)
                else CallResult.Fail(json.optString("error", "unknown"), json.optDouble("value", 0.0))
            } catch (e: JSONException) {
                CallResult.Fail("unknown")
            }
        }

    // ---- internals --------------------------------------------------------

    private sealed interface Raw {
        data class Body(val text: String) : Raw
        data class Error(val error: AuthError) : Raw
    }

    private suspend fun rpc(function: String, body: JSONObject): ApiResult =
        when (val raw = post(function, body)) {
            is Raw.Error -> ApiResult.Failure(raw.error)
            is Raw.Body -> try {
                parse(JSONObject(raw.text))
            } catch (e: JSONException) {
                ApiResult.Failure(AuthError.UNKNOWN)
            }
        }

    private suspend fun post(function: String, body: JSONObject): Raw =
        withContext(Dispatchers.IO) {
            if (AppConfig.supabaseUrl.isBlank() || AppConfig.anonKey.isBlank()) {
                return@withContext Raw.Error(AuthError.NOT_CONFIGURED)
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (URL("${AppConfig.supabaseUrl}/rest/v1/rpc/$function")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    doOutput = true
                    setRequestProperty("apikey", AppConfig.anonKey)
                    // legacy anon keys are JWTs (start with "eyJ"); new publishable
                    // keys must be sent only in the apikey header
                    if (AppConfig.anonKey.startsWith("eyJ")) {
                        setRequestProperty("Authorization", "Bearer ${AppConfig.anonKey}")
                    }
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code == 404) {
                    // PostgREST: function not found -> the SQL file was not run in Supabase yet
                    return@withContext Raw.Error(AuthError.SERVER_SETUP)
                }
                if (code !in 200..299) {
                    return@withContext Raw.Error(AuthError.UNKNOWN)
                }
                Raw.Body(text)
            } catch (e: IOException) {
                Raw.Error(AuthError.NETWORK)
            } finally {
                connection?.disconnect()
            }
        }

    private fun parse(json: JSONObject): ApiResult {
        if (json.optBoolean("ok", false)) {
            val userJson = json.optJSONObject("user") ?: return ApiResult.Failure(AuthError.UNKNOWN)
            val token = json.optString("token", "").takeIf { it.isNotBlank() && it != "null" }
            return ApiResult.Success(AppUser.fromJson(userJson), token)
        }

        val error = when (json.optString("error")) {
            "invalid_phone" -> AuthError.INVALID_PHONE
            "invalid_pin" -> AuthError.INVALID_PIN
            "invalid_name" -> AuthError.INVALID_NAME
            "invalid_address" -> AuthError.INVALID_ADDRESS
            "invalid_pay_number" -> AuthError.INVALID_PAY_NUMBER
            "user_not_found" -> AuthError.USER_NOT_FOUND
            "wrong_pin" -> AuthError.WRONG_PIN
            "locked" -> AuthError.LOCKED
            "blocked" -> AuthError.BLOCKED
            "device_mismatch" -> AuthError.DEVICE_MISMATCH
            "device_in_use" -> AuthError.DEVICE_IN_USE
            "phone_exists" -> AuthError.PHONE_EXISTS
            "invalid_session" -> AuthError.INVALID_SESSION
            else -> AuthError.UNKNOWN
        }
        return ApiResult.Failure(error, json.optInt("extra", 0))
    }
}
