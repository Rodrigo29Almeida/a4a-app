package com.audiology.a4a

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    //~~ socket config ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private val socket_IP = "192.168.1.99"//ip da rede
    private val socket_port = 5555


    //~~ Buttons ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private lateinit var startButton: Button
    private lateinit var stopButton: Button


    //~~ Recordring ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    companion object {
        var recording           =   false
        var receiving           =   false
        var recording_compress  =   false
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        val clientHighLevelFunctions = client_high_level_functions(socket_IP, socket_port, this)

        startButton.setOnClickListener {
            recording               =   true
            recording_compress      =   true
            receiving               =   true
            startButton.isEnabled   =   false
            stopButton.isEnabled    =   true

            val permissions = arrayOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE
            )
            val permissionsToRequest = mutableListOf<String>()

            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(permission)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                // Solicitar as permissões, caso não tenha sido feito ainda
                requestPermissions(permissionsToRequest.toTypedArray(), 0)
            } else {
                //~~ Começa a comunicar com o servidor ~~~~~~~~~~~~~~~~~~~~

                //clientHighLevelFunctions.client_wire_c2s(1024, 200F)


                clientHighLevelFunctions.client_compress_c2s2c(1024, 200F)

            }
        }

        stopButton.setOnClickListener {
            //enviar o recording=false para o client_high
            recording           =   false
            receiving           =   false
            recording_compress  =   false
            //clientHighLevelFunctions.closeAudioSendWire()
            clientHighLevelFunctions.closeAudioSendCompress()
            stopRecording()
        }
    }


    private fun stopRecording() {
        recording = false
        startButton.isEnabled = true
        stopButton.isEnabled = false
    }
}
