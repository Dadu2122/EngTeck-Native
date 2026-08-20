package com.shreeyog.engteck.live

import android.content.Context
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AgoraLiveAudio {
    private const val AGORA_APP_ID = "5b0232817d3b4c33a96d515a476e6a5f"
    private const val AGORA_TOKEN_SERVER = "https://shreeyog-agora-token-server.vercel.app/api/generate-token"
    // Must match LIVE_CHANNEL in the website (index.html) so the native app and
    // the website end up on the exact same Agora channel.
    private const val LIVE_CHANNEL_PREFIX = "ENGLISH_HUB_LIVE_"

    private var engine: RtcEngine? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onJoined: ((uid: Int) -> Unit)? = null
    var onUserJoined: ((uid: Int) -> Unit)? = null
    var onUserLeft: ((uid: Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            onJoined?.invoke(uid)
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            onUserJoined?.invoke(uid)
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            onUserLeft?.invoke(uid)
        }
        override fun onError(err: Int) {
            onError?.invoke(err.toString())
        }
    }

    private fun ensureEngine(context: Context): RtcEngine {
        if (engine == null) {
            val config = RtcEngineConfig()
            config.mContext = context.applicationContext
            config.mAppId = AGORA_APP_ID
            config.mEventHandler = eventHandler
            engine = RtcEngine.create(config)
            engine?.enableAudio()
            engine?.disableVideo()
        }
        return engine!!
    }

    private suspend fun fetchToken(channel: String, uid: Int, role: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AGORA_TOKEN_SERVER?channel=$channel&uid=$uid&role=$role")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().readText()
            val json = JSONObject(response)
            if (json.has("token")) json.getString("token") else null
        } catch (e: Exception) {
            null
        }
    }

    // channel: pass the short name (e.g. "default") — the website's
    // "ENGLISH_HUB_LIVE_" prefix is added automatically so both sides match.
    // role: "host" for the teacher, "audience" for a student.
    fun join(context: Context, channel: String, uid: Int, role: String = "host") {
        val fullChannel = LIVE_CHANNEL_PREFIX + channel
        scope.launch {
            val token = kotlinx.coroutines.withTimeoutOrNull(12000) { fetchToken(fullChannel, uid, role) }
            if (token == null) {
                onError?.invoke("TOKEN_FETCH_FAILED")
                return@launch
            }
            val eng = ensureEngine(context)
            val options = ChannelMediaOptions()
            options.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            options.clientRoleType = if (role == "audience") Constants.CLIENT_ROLE_AUDIENCE else Constants.CLIENT_ROLE_BROADCASTER
            options.publishMicrophoneTrack = role != "audience"
            options.autoSubscribeAudio = true

            var joinedOrErrored = false
            val originalOnJoined = onJoined
            val originalOnError = onError
            onJoined = { uidResult -> joinedOrErrored = true; originalOnJoined?.invoke(uidResult) }
            onError = { err -> joinedOrErrored = true; originalOnError?.invoke(err) }

            eng.joinChannel(token, fullChannel, uid, options)

            kotlinx.coroutines.delay(15000)
            if (!joinedOrErrored) {
                onError?.invoke("JOIN_TIMEOUT — no response from Agora after 15s. Check internet connection.")
            }
        }
    }

    fun setMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
    }

    fun leave() {
        engine?.leaveChannel()
    }
}
