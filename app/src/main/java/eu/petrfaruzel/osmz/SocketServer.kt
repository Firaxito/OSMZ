package eu.petrfaruzel.osmz

import android.util.Log
import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
import java.io.*
import java.net.ServerSocket
import java.net.SocketException
import java.util.*
import java.util.concurrent.Semaphore


// Kód MainActivity je taktéž přepsaný do kotlinu, avšak změny nejsou reálně žádné,
// takže jsem se rozhodl jej neuploadovat
// V Android manifestu však byla potřeba nastavit requestLegacyExternalStorage="true" pro API>=29


class SocketServer(
    val maxAvailableThreads: Int = 4,
    val port: Int = 12345,
    val stateChangedListener: OnStateChangedListener? = null,
    val requestAcceptedListener: OnRequestAcceptedListener? = null
) : Thread() {

    private var serverSocket: ServerSocket = ServerSocket(port)
    private val semaphore = Semaphore(maxAvailableThreads, true)

    interface OnStateChangedListener {
        fun onRunningChanged(state: Boolean)
    }

    interface OnRequestAcceptedListener {
        fun onRequestAccepted(
            ipAddress: String,
            date: Calendar,
            requestType: RequestType,
            path: String?,
            httpProtocol: String,
            responseCode: ResponseCode,
        )
    }

    // Send data from worker to server
    // In synchronized manner, making it queue
    val onRequestProcessedListener =  object : RequestWorker.OnRequestProcessed {
        override fun onRequestProcessed(
            headerInfo: RequestWorker.HeaderInfo,
            requestIP: String
        ) {
            // Send data from server to Activity
            requestAcceptedListener?.onRequestAccepted(
                ipAddress = requestIP,
                date = headerInfo.responseTime,
                requestType = headerInfo.requestType,
                path = headerInfo.path,
                httpProtocol = headerInfo.httpProtocol,
                responseCode = headerInfo.responseCode
            )
        }
    }

    var isRunning: Boolean = false
        private set

    override fun run() {
        Log.d(TAG, "Server Waiting for requests")
        isRunning = true
        stateChangedListener?.onRunningChanged(true)
        try {
            while (isRunning) {
                val s = serverSocket.accept()
                Log.d(TAG, "Socket Accepted")
                Log.d(TAG, "Creating ${maxAvailableThreads -  semaphore.availablePermits() + 1}. worker")
                RequestWorker(
                    s,
                    semaphore,
                    onRequestProcessedListener
                   ).start()
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            isRunning = false
            stateChangedListener?.onRunningChanged(false)
            serverSocket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Error, probably interrupted in accept(), see log")
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "SOCKET_SERVER"

    }
}