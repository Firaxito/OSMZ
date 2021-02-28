package eu.petrfaruzel.osmz

import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore

class RequestWorker(
    val socket: Socket,
    val semaphore: Semaphore,
    val requestListener: OnRequestProcessed?
) : Thread() {

    companion object {
        const val TAG = "REQUEST_WORKER"
        const val HTTP_PROCOTOL = "HTTP/1.0"
    }

    interface OnRequestProcessed {
        fun onRequestProcessed(
            headerInfo: HeaderInfo,
            requestIP: String
        )
    }

    data class HeaderInfo(
        val path : String?,
        val mimeType : String,
        val responseCode : ResponseCode,
        val responseTime : Calendar,
        val requestType : RequestType,
        val contentLength: Int,
        val httpProtocol : String = HTTP_PROCOTOL,
        ){

        fun getStringResponse() : String{
            val df: DateFormat = SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.getDefault())

            return StringBuilder()
                .appendLine("$httpProtocol ${responseCode.responseText}")
                .appendLine("Date: ${df.format(responseTime.time)}")
                .appendLine("Content-Type: $mimeType")
                .appendLine("Content-Length: $contentLength")
                .appendLine()
                .toString()
        }
    }

    override fun run() {
        Log.d(TAG, "Creating new worker with id #$id")
        var isLockAcquired = true

        try {
            if (!semaphore.tryAcquire()) {
                isLockAcquired = false
                writeGETResponse(socket, null, null, ResponseCode.CODE_503)
            } else {

                Log.d("SEMAPHORE", "Remaning: ${semaphore.availablePermits()}")
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                var clientInput: String?

                var path: String? = null
                var requestType: RequestType? = null
                var httpProtocol: String? = null

                // Parsing request
                while (!input.readLine().also { clientInput = it }.isNullOrEmpty()) {
                    // Checking first row of request
                    if (requestType == null) {
                        requestType = getRequestTypeFromClientHeader(clientInput!!)

                        // Checking for invalid client header
                        if (requestType == RequestType.UNKNOWN) {
                            Log.d("CLIENT REQUEST", "Invalid or unknown")
                            continue
                        }
                        path = getPathFromClientHeader(requestType, clientInput!!)
                        if(path != null)
                            httpProtocol = getHttpProtocol(path, clientInput!!)
                    }

                    Log.d("CLIENT REQUEST", clientInput!!)
                }

                if (requestType != RequestType.UNKNOWN && path != null) {
                    Log.d("Retrieved RT", requestType.toString())
                    Log.d("Retrieved Path", path.toString())
                    writeGETResponse(socket, path, httpProtocol, ResponseCode.CODE_200)
                }
            }

        } catch (e: IOException) {
            if (socket != null && socket!!.isClosed)
                Log.d(TAG, "Normal exit"
            ) else {
                Log.d(TAG, "Error")
                e.printStackTrace()
            }
        } finally {
            Log.d("SERVER", "Socket Closed")
            if(!socket.isClosed) socket.close()
            if(isLockAcquired) semaphore.release()
        }

    }

    // Can be updated later with non-obsolete android storage API
    private fun getFileFromStorage(path: String): File {
        return File("${Environment.getExternalStorageDirectory()}${path}")
    }

    private fun getFileHeader(
        path: String?,
        responseCode: ResponseCode = ResponseCode.CODE_200
    ): HeaderInfo {

        var realResponseCode = responseCode
        var contentLength: Int = 0

        if (path != null && realResponseCode == ResponseCode.CODE_200) {
            val file = getFileFromStorage(path)
            Log.d("DATA", file.toString())

            // Existing file 200
            if (file.exists()) {
                contentLength = file.readBytes().size
            } else {
                realResponseCode = ResponseCode.CODE_404
            }
        }

        when(realResponseCode){
            ResponseCode.CODE_404 -> contentLength = get404Content().size
            ResponseCode.CODE_503 -> contentLength = get503Content().size
        }

        return HeaderInfo(
            path = path,
            mimeType = getMimeType(path),
            responseCode = realResponseCode,
            responseTime = Calendar.getInstance(),
            contentLength = contentLength,
            requestType = RequestType.GET,
        )
    }

    private fun getContent(responseCode : ResponseCode, path : String?) : ByteArray{
        return when(responseCode){
            ResponseCode.CODE_200 -> {
                getFileContent(path)
            }
            ResponseCode.CODE_404 -> get404Content()
            ResponseCode.CODE_503 -> get503Content()
        }
    }

    private fun getFileContent(path: String?): ByteArray {
        if (path != null) {
            val file = getFileFromStorage(path)
            if (file.exists()) {
                return file.readBytes()
            }
        }

        return get404Content()
    }

    private fun get404Content(): ByteArray {
        val file = getFileFromStorage("/404.html")
        return if (file.exists()) {
            file.readBytes()
        } else {
            """
        <html>
        <body>
        <p>404 Not found</p>
        </body>
        </html>
    """.trimIndent().encodeToByteArray()
        }
    }

    private fun get503Content(): ByteArray {
        return """
        <html>
        <body>
        <p>503 Server is busy</p>
        </body>
        </html>
    """.trimIndent().encodeToByteArray()
    }

    // Handles response all together
    private fun writeGETResponse(socket: Socket, path: String?, httpProtocol: String?,  expectedResponse: ResponseCode = ResponseCode.CODE_200) {
        var modifiedPath = path
        if (path == "/") modifiedPath = "/index.html"

        val output = socket.getOutputStream()
        val out = BufferedWriter(OutputStreamWriter(output))

        var header = getFileHeader(modifiedPath, expectedResponse)
        if(httpProtocol != null) header = header.copy(httpProtocol = httpProtocol)

        out.write(header.getStringResponse())
        out.flush()
        Log.d("SERVER RESPONSE HEADER", header.getStringResponse())
        val content = getContent(header.responseCode, modifiedPath)
        output.write(content)
        output.flush()
        requestListener?.onRequestProcessed(
            headerInfo = header,
            requestIP = (socket.remoteSocketAddress as InetSocketAddress).address.toString().removePrefix("/")
        )
        if(header.mimeType.contains("text")) Log.d("SERVER RESPONSE CONTENT", content.decodeToString())
        else Log.d("SERVER RESPONSE CONTENT", "Non-textual content (image probably)")
    }


    private fun getMimeType(url: String?): String {
        var type = "text/html"
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: type
        }
        return type
    }

    // Expecting valid client header
    private fun getPathFromClientHeader(requestType: RequestType, clientHeader: String): String? {
        if (requestType == RequestType.GET)
            return clientHeader.substringAfter("GET").substringBefore("HTTP").trim()
        else if (requestType == RequestType.POST)
            return clientHeader.substringAfter("POST").substringBefore("HTTP").trim()
        else return null
    }

    private fun getHttpProtocol(requestPath : String, clientHeader: String) : String{
        return clientHeader.substringAfter(requestPath).trim()
    }

    private fun getRequestTypeFromClientHeader(clientHeader: String): RequestType {
        if (clientHeader.contains("GET")) return RequestType.GET
        else if (clientHeader.contentEquals("POST")) return RequestType.POST
        else return RequestType.UNKNOWN
    }


    class LockObject(private val listener : OnStateChanged? = null) {

        interface OnStateChanged{
            fun onStateChanged(isLocked : Boolean)
        }

        var isLocked = false
            private set

        fun lock() {
            isLocked = true
            listener?.onStateChanged(true)
        }

        fun unlock() {
            isLocked = false
            listener?.onStateChanged(false)
        }
    }

}