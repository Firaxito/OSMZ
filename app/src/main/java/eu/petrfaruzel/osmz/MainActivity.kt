package eu.petrfaruzel.osmz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.petrfaruzel.osmz.enums.RequestType
import eu.petrfaruzel.osmz.enums.ResponseCode
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
            }
        }

        if (v.id == R.id.button2) {
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
            else -> {
            }
        }
    }

    companion object {
        private const val READ_EXTERNAL_STORAGE = 1
    }
}