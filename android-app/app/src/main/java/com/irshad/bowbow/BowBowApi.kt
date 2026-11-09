package com.irshad.bowbow

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HTTP client for the BOW BOW FastAPI backend. No API keys in the app —
 *  the backend holds the OpenRouter and Supabase keys server-side. */
class BowBowApi(baseUrl: String) {

    private val base = baseUrl.trimEnd('/')
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class ChatRequest(
        val message: String,
        val language: String,
        val user_id: String
    )

    private data class ChatResponse(
        val reply: String?,
        val model: String?,
        val language: String?
    )

    /** A reminder row from GET /reminders. Field names match the
     *  backend JSON exactly (snake_case). */
    data class Reminder(
        val id: String = "",
        val title: String = "",
        val scheduled_at: String = "",
        val status: String = ""
    )

    /** A task row from GET /tasks. */
    data class Task(
        val id: String = "",
        val title: String = "",
        val status: String = ""
    )

    /** Send a message to the assistant and return its reply text. */
    suspend fun chat(message: String, language: String, userId: String): String =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(ChatRequest(message, language, userId))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$base/chat")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Server error ${response.code}: ${text.take(200)}")
                }
                val parsed = gson.fromJson(text, ChatResponse::class.java)
                val reply = parsed.reply?.trim().orEmpty()
                if (reply.isBlank()) throw IOException("Empty reply from server")
                reply
            }
        }

    /** Pending reminders, for the polling worker. */
    suspend fun listReminders(): List<Reminder> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$base/reminders?status=pending")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Server error ${response.code}: ${text.take(200)}")
                }
                gson.fromJson(text, Array<Reminder>::class.java)
                    ?.toList().orEmpty()
            }
        }

    /** Mark a reminder "sent" after its local notification was shown,
     *  so it is only shown once. */
    suspend fun markReminderSent(id: String) =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("status" to "sent"))
                .toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$base/reminders/$id")
                .patch(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw IOException("Server error ${response.code}: ${text.take(200)}")
                }
            }
        }

    /** Tasks waiting on the user's approval, for the polling worker. */
    suspend fun listTasksAwaitingApproval(): List<Task> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$base/tasks?status=awaiting_approval")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Server error ${response.code}: ${text.take(200)}")
                }
                gson.fromJson(text, Array<Task>::class.java)
                    ?.toList().orEmpty()
            }
        }
}
