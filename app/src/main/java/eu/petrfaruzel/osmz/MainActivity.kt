package eu.petrfaruzel.osmz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*


class MainActivity : AppCompatActivity(), View.OnClickListener {

    private var server: SocketServer? = null
    private var logAdapter: LogAdapter = LogAdapter()

    private lateinit var mStatus: TextView
    private lateinit var mClearLogs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mStatus = findViewById(R.id.status_text)

        val btn1: Button = findViewById(R.id.button1)
        val btn2: Button = findViewById(R.id.button2)

        btn1.setOnClickListener(this)
        btn2.setOnClickListener(this)

        val recyclerView: RecyclerView = findViewById(R.id.recycler)
        recyclerView.setDivider()
        recyclerView.adapter = logAdapter

        mClearLogs = findViewById(R.id.clear_logs_button)
        mClearLogs.setOnClickListener(this)
    }

    private fun startSocketServer() {
        if (server != null || server?.isRunning == true) return
        // Init and start
        server = SocketServer(
            stateChangedListener = object : SocketServer.OnStateChangedListener {
                override fun onRunningChanged(state: Boolean) {
                    Handler(Looper.getMainLooper()).post {
                        when (state) {
                            false -> mStatus.text = getString(R.string.status_stopped)
                            true -> mStatus.text = getString(R.string.status_running)
                        }
                    }
                }
            },
            requestAcceptedListener = object : SocketServer.OnRequestAcceptedListener {
                @Synchronized
                override fun onRequestAccepted(
                    ipAddress: String,
                    date: Calendar,
                    requestType: RequestType,
                    path: String?,
                    httpProtocol: String,
                    responseCode: ResponseCode
                ) {
                    Handler(Looper.getMainLooper()).post {
                        mClearLogs.visibility = View.VISIBLE
                        logAdapter.addLogItem(
                            LogItem(
                                ipAddress = ipAddress,
                                date = date.time,
                                requestType = requestType,
                                path = path,
                                httpVersion = httpProtocol,
                                responseCode = responseCode
                            )
                        )
                    }
                }
            }
        ).also { it.start() }
    }

    override fun onClick(v: View) {
        if (v.id == R.id.button1) {
            val permissionCheck =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), READ_EXTERNAL_STORAGE
                )
            } else {
                startSocketServer()
                setupCamera()
            }
        }

        if (v.id == R.id.button2) {
            if(timer != null){
                timer?.cancel()
                timer?.purge()
                timer = null
            }
            server?.close()
            try {
                server?.join()
                server = null
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        if (v.id == R.id.clear_logs_button) {
            logAdapter.clearData()
            mClearLogs.visibility = View.GONE
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSocketServer()
            }
            PERMISSION_CAMERA, -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        setupCamera()
                } else {
                    this.finish()
                }
            }
            else -> {
            }
        }
    }


    // ######## Camera stuff ###########


    /** Check if this device has a camera */
    private fun checkCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }

    /** A safe way to get an instance of the Camera object. */
    fun getCameraInstance(): Camera? {
        return try {
            Camera.open(0) // attempt to get a Camera instance
        } catch (e: Exception) {
            // Camera is not available (in use or does not exist)
            null // returns null if camera is unavailable
        }
    }

    private var mCamera: Camera? = null
    private var mPreview: CameraPreview? = null

    fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun setupCamera() {
        val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )

        if (!hasPermissions(this, *PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CAMERA)
            return;
        }

        // Create an instance of Camera
        mCamera = getCameraInstance()
        mCamera?.Size(1280, 720)
        mCamera?.parameters = mCamera?.parameters.apply { this?.setPictureSize(1280, 720) }

        mPreview = mCamera?.let {
            // Create our Preview view
            CameraPreview(this, it)
        }

        // Set the Preview view as the content of our activity.
        mPreview?.also {
            val preview: FrameLayout = findViewById(R.id.camera_preview)
            preview.addView(it)
        }

        mCamera?.startPreview();
        task = object : TimerTask() {
            override fun run() {
                Handler(Looper.getMainLooper()).post {
                    if(isCameraReady) {
                        mCamera?.takePicture(null, null, mPicture)
                        isCameraReady = false
                    }
                }
            }
        }
        timer = Timer().also {
            it.schedule(task, 5000L, 100) // Trying max of 10 fps
        }
    }

    // Main callback
    private val mPicture : Camera.PictureCallback = Camera.PictureCallback { data, _ ->
        try {
            CameraStream.setNewCameraImage(data)
            /*
             val pictureFile: File = getFileFromStorage("camera.jpg")
             Log.d("PICTURE", pictureFile.toString() + " ")
            val fos = FileOutputStream(pictureFile)
            fos.write(data)
            fos.close()
            */
        } catch (e: FileNotFoundException) {
            Log.d(TAG, "File not found: ${e.message}")
        } catch (e: IOException) {
            Log.d(TAG, "Error accessing file: ${e.message}")
        }

        mCamera?.startPreview();
        isCameraReady = true
    }

    private var isCameraReady = true // Flag for camera, that is release after picter callback
    private var timer : Timer? = null
    private var task : TimerTask? = null

    //#############################


    companion object {
        private const val READ_EXTERNAL_STORAGE = 1
        private const val PERMISSION_CAMERA = 2
        private const val TAG = "MainActivity"
    }
}