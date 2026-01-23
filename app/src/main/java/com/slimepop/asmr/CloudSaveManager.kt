package com.slimepop.asmr

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import org.json.JSONObject

object CloudSaveManager {
    private const val TAG = "CloudSaveManager"
    private const val SAVE_NAME = "slime_pop_progress"

    fun saveToCloud(context: Context) {
        val activity = context as? Activity ?: return
        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        
        val data = JSONObject().apply {
            put("coins", Prefs.getCoins(context))
            put("owned_iap", Prefs.getOwnedIapCsv(context))
            put("equipped_skin", Prefs.getEquippedSkinId(context))
            put("equipped_sound", Prefs.getEquippedSoundId(context))
            put("ads_removed", Prefs.getAdsRemoved(context))
        }.toString().toByteArray()

        snapshotsClient.open(SAVE_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnSuccessListener { result ->
                val snapshot = result.data
                if (snapshot != null) {
                    writeSnapshot(snapshotsClient, snapshot, data)
                }
            }
    }

    private fun writeSnapshot(client: SnapshotsClient, snapshot: Snapshot, data: ByteArray) {
        snapshot.snapshotContents.writeBytes(data)
        val metadataChange = SnapshotMetadataChange.Builder()
            .setDescription("Slime Pop Progress")
            .build()
        client.commitAndClose(snapshot, metadataChange)
            .addOnFailureListener { Log.e(TAG, "Failed to commit cloud save", it) }
    }

    fun loadFromCloud(context: Context, onLoaded: () -> Unit) {
        val activity = context as? Activity ?: return
        val snapshotsClient = PlayGames.getSnapshotsClient(activity)
        snapshotsClient.open(SAVE_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED)
            .addOnSuccessListener { result ->
                val snapshot = result.data
                if (snapshot != null) {
                    try {
                        val bytes = snapshot.snapshotContents.readFully()
                        if (bytes.isNotEmpty()) {
                            val json = JSONObject(String(bytes))
                            Prefs.setCoins(context, json.optInt("coins", Prefs.getCoins(context)))
                            Prefs.setOwnedIapCsv(context, json.optString("owned_iap", Prefs.getOwnedIapCsv(context)))
                            Prefs.setEquippedSkinId(context, json.optString("equipped_skin", Prefs.getEquippedSkinId(context)))
                            Prefs.setEquippedSoundId(context, json.optString("equipped_sound", Prefs.getEquippedSoundId(context)))
                            Prefs.setAdsRemoved(context, json.optBoolean("ads_removed", Prefs.getAdsRemoved(context)))
                            onLoaded()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to read cloud save", e)
                    }
                }
            }
    }
}
