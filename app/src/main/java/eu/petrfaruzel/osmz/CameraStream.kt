package eu.petrfaruzel.osmz

import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import java.io.BufferedWriter
import java.io.IOException
import java.net.Socket
import java.net.SocketException


class CameraStream {

    companion object {
        private const val TAG = "CameraStream"
        private val cameraListeners: ArrayList<Socket> = arrayListOf()
        fun detachSocket(socket : Socket?) {
            if(socket != null && cameraListeners.contains(socket))
            cameraListeners.remove(socket)
        }

        fun attachSocket(socket : Socket) {
            cameraListeners.add(socket)
        }

        fun setNewCameraImage(image: ByteArray) {
            cameraListeners.forEach {
                Thread {
                    try {
                        val output = it.getOutputStream()
                        output.write("Content-Type: image/jpeg\n\n".toByteArray())
                        output.write(image)
                        output.flush()
                        output.write("--${RequestWorker.MJPEG_BOUNDARY}".toByteArray())
                        output.flush()
                    } catch (e : Exception){
                        Log.i(TAG, "Connection broken: $e")
                        it.close()
                        detachSocket(it)
                    }
                }.start()
            }
        }
    }
}