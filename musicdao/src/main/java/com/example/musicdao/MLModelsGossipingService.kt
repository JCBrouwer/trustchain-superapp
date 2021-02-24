package com.example.musicdao
import com.example.federated_ml.WeakLearner
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.example.musicdao.ipv8.MusicCommunity
import kotlinx.coroutines.*
import nl.tudelft.ipv8.android.IPv8Android
import kotlin.system.exitProcess

class MLModelsGossipingService: Service() {
    // placeholder for now
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var sentOwnModel = false

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): MLModelsGossipingService = this@MLModelsGossipingService
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()

        // if we want every peer to send own model for a random walk
        // probably an overkill for now
//        scope.launch{
//            sendOwnModel()
//        }
//        this.sentOwnModel = true

        scope.launch {
            processIncomingModels()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()

        // We need to kill the app as IPv8 is started in Application.onCreate
        exitProcess(0)
    }

    /**
     * This is a very simplistic way to crawl all chains from the peers you know
     */
    private suspend fun processIncomingModels() {
        // here should be something like
        // weakLeaner.updateWithNewModel(incomingModel)

        val musicCommunity = IPv8Android.getInstance().getOverlay<MusicCommunity>()
        while (scope.isActive) {
            musicCommunity?.communicateOnlineModels()
            delay(10000)
        }
        TODO("Not yet implemented")
    }


}
