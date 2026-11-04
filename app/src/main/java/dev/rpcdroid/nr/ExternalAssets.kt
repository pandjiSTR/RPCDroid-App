package dev.rpcdroid.nr

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** POST /applications/{id}/external-assets: ubah URL gambar jadi "mp:external/..." yang diterima gateway. */
object ExternalAssets {
    fun resolve(http: OkHttpClient, token: String, appId: String, imageUrl: String, cb: (String?) -> Unit) {
        val body = JSONObject().put("urls", JSONArray().put(imageUrl)).toString()
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://discord.com/api/v10/applications/$appId/external-assets")
            .header("Authorization", token).header("User-Agent", DiscordGateway.UA)
            .post(body).build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = cb(null)
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val path = try {
                        if (!it.isSuccessful) null
                        else JSONArray(it.body?.string().orEmpty()).optJSONObject(0)?.optString("external_asset_path")
                    } catch (e: Exception) { null }
                    cb(if (path.isNullOrEmpty()) null else if (path.startsWith("mp:")) path else "mp:$path")
                }
            }
        })
    }
}
