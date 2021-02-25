package eu.petrfaruzel.osmz

import android.util.Log
import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
import java.io.*
import java.net.ServerSocket
import java.net.SocketException
import java.util.*


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
    private val locks: List<RequestWorker.LockObject>

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


    init {
        // Fill list with locks
        val initLocks = arrayListOf<RequestWorker.LockObject>()
        for (i in 0 until maxAvailableThreads) {
            initLocks.add(
                RequestWorker.LockObject(
                    listener = object : RequestWorker.LockObject.OnStateChanged {
                        override fun onStateChanged(isLocked: Boolean) {
                            Log.d(TAG, "Lock state isLocked changed to: $isLocked")
                            Log.d(TAG, "Remaining connections (workers): ${getRemainingClientsConnections()}")
                        }
                    })
            )
        }
        locks = initLocks
    }

    var isRunning: Boolean = false
        private set


    @Synchronized
    private fun getNextAvailableLockItem(): RequestWorker.LockObject? {
        for (lock in locks) {
            if (!lock.isLocked) {
                lock.lock()
                return lock
            }
        }
        return null // Server cannot process more requests
    }

    override fun run() {
        Log.d(TAG, "Server Waiting for requests")
        isRunning = true
        stateChangedListener?.onRunningChanged(true)
        try {
            while (isRunning) {
                val s = serverSocket.accept()
                Log.d(TAG, "Socket Accepted")
                RequestWorker(
                    s,
                    getNextAvailableLockItem(),
                    object : RequestWorker.OnRequestProcessed {
                        // Send data from worker to server
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
                    }).start()
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

    fun getRemainingClientsConnections(): Int {
        var total = 0
        for (lock in locks) {
            if (!lock.isLocked) total += 1;
        }
        return total;
    }

    companion object {
        private const val TAG = "SOCKET_SERVER"

    }
}