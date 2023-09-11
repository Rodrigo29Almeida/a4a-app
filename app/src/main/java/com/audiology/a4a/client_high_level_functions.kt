package com.audiology.a4a

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.audiology.a4a.MainActivity.Companion.recording
import com.audiology.a4a.MainActivity.Companion.receiving
import com.audiology.a4a.MainActivity.Companion.recording_compress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*


class client_high_level_functions(
    val socket_IP: String,
    val socket_port: Int,
    val Context: Context
) {

    //~~ datagram config ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    val datagram_sz: Int = 8096

    private val wire_data_pad_str = "__wire__"
    private val wire_stop_flag = "__were__"
    private val compress_data_pad_str = "__comp__"
    private val compress_stop_flag = "pmoc"

    private val after_timestamp_char = "&"
    private val data_header_end_str = "__"


    //~~ pidge config ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private val n_msgs_per_pidges = 10
    private val n_entries_per_pidges = 5


    //~~ audio config ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private val chunk = 1024                              // Each chunk will consist of 1024 samples
    private val sample_format = AudioFormat.ENCODING_PCM_16BIT    // 16 bits per sample
    private val channels = AudioFormat.CHANNEL_IN_STEREO     // Number of audio channels
    private val fs = 44100                             // Record at 44100 samples per second

    //private val BUFFER_SIZE = AudioRecord.getMinBufferSize(fs, channelConfig, audioFormat)

    var socket: DatagramSocket? = null
    var audioRecord: AudioRecord? = null


    //~~ threads ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    var receivingThread: Thread? = null


    //~~ play audio ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    var audioTrack: AudioTrack? = null


    //~~ mid level ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    val client_mid_level_functions = client_mid_level_functions()


    //~~ hello datagram ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    fun hello_function() {

        /*if (socket == null) {
            socket = DatagramSocket()
        }*/


        //~~ Hello ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        // Envia a mensagem para o servidor

        val helloMessage = "hello".toByteArray()
        val sendHelloPacket = DatagramPacket(
            helloMessage,
            helloMessage.size,
            InetAddress.getByName(socket_IP),
            socket_port
        )
        socket?.send(sendHelloPacket)


        // Recebe a resposta do servidor

        val receiveData = ByteArray(datagram_sz)
        val receivePacket = DatagramPacket(receiveData, receiveData.size)
        socket?.receive(receivePacket)
        val response = String(receivePacket.data, 0, receivePacket.length)


        // Exibir a resposta do servidor

        println(response)
    }


    fun client_wire_c2s(chunk: Int, sleep_fraction: Float) {

        //~~ Verificar permissão ~~~~~~~~~~~~~~~~~~~~~~~~~~

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionCheck =
                ContextCompat.checkSelfPermission(Context, Manifest.permission.RECORD_AUDIO)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("RECORD_AUDIO permission not granted")
            }
        }


        var sendingThread = Thread {

            if (socket == null) {
                socket = DatagramSocket()
            }


            //~~ Hello ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

            hello_function()


            //~~ 'wire' command ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

            val wireMessage = "wire".toByteArray(Charsets.UTF_8)
            val sendWirePacket = DatagramPacket(
                wireMessage,
                wireMessage.size,
                InetAddress.getByName(socket_IP),
                socket_port
            )
            socket?.send(sendWirePacket)


            val frames = ByteArray(datagram_sz)
            Arrays.fill(frames, 0.toByte())


            if (audioRecord == null) {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    fs,
                    channels,
                    sample_format,
                    datagram_sz
                )
            }


            audioRecord?.startRecording()


            var timestamp = 0


            while (recording) {
                var data: ByteArray?
                val bytesRead = audioRecord!!.read(frames, 0, datagram_sz)

                if (frames.size >= datagram_sz) {

                    //~~ Amplitude media - conferir audio ~~~~~~~~~~~~~~~~~~~~~~~~

                    //amplitude_audio(bytesRead, frames)


                    val header_type = client_mid_level_functions.get_header_header_sz(
                        wire_data_pad_str,
                        timestamp,
                        sample_format,
                        channels,
                        after_timestamp_char,
                        data_header_end_str
                    )
                    val header = header_type.first
                    val header_sz = header_type.second

                    val msg_sz = datagram_sz - header_sz


                    data = ByteArray(msg_sz)
                    System.arraycopy(frames, 0, data, 0, msg_sz)
                    data = header + data
                    Arrays.fill(frames, 0.toByte())


                    val packet = DatagramPacket(
                        data,
                        data.size,
                        InetAddress.getByName(socket_IP),
                        socket_port
                    )


                    socket?.send(packet)
                    println("depois de enviar o pacote")
                    timestamp++
                } else {
                    Thread.sleep((sleep_fraction * datagram_sz / fs).toLong())
                }
            }
        }.start()
    }


    fun client_compress_c2s2c(chunk: Int, sleep_fraction: Float) {

        //~~ Verificar permissão ~~~~~~~~~~~~~~~~~~~~~~~~~~

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionCheck =
                ContextCompat.checkSelfPermission(Context, Manifest.permission.RECORD_AUDIO)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("RECORD_AUDIO permission not granted")
            }
        }


        GlobalScope.launch(Dispatchers.IO) {

            //~~ Thread para enviar áudio ~~~~~~~~~~~~~~~~~~~~~~

            val lambdaThread_sender = Thread {
                // code to be executed in the new thread

                if (socket == null) {
                    socket = DatagramSocket()
                }


                hello_function()


                val cmd_str = "compress".toByteArray(Charsets.UTF_8)
                val sendWirePacket = DatagramPacket(
                    cmd_str,
                    cmd_str.size,
                    InetAddress.getByName(socket_IP),
                    socket_port
                )
                socket?.send(sendWirePacket)


                val frames = ByteArray(datagram_sz)


                if (audioRecord == null) {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        fs,
                        channels,
                        sample_format,
                        datagram_sz
                    )
                }


                audioRecord?.startRecording()


                client_compress_c2s(frames, sleep_fraction, fs, channels, sample_format)

                println("Thread sender is running")
            }


            //~~ Thread para receber áudio ~~~~~~~~~~~~~~~~~~~~~~

            val lambdaThread_receiver = Thread {
                // code to be executed in the new thread

                val data_s2c        = ByteArray(datagram_sz)
                val pidges_filled   = false


                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    fs,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    sample_format,
                    datagram_sz,
                    AudioTrack.MODE_STREAM
                )


                client_compress_s2c(data_s2c, pidges_filled)


                //audioTrack?.play() //Play Audio

                println("Thread receiver is running")
            }


            // starting the thread
            lambdaThread_sender.start()

            // starting the thread
            lambdaThread_receiver.start()


            // wait for the thread to finish
            lambdaThread_sender.join()

            // wait for the thread to finish
            lambdaThread_receiver.join()
        }
    }


    fun client_compress_c2s(frames: ByteArray, sleep_fraction: Float, fs:Int, channelConfig:Int, audioFormat:Int) {

        var timestamp_out = 0


        while (recording_compress) {

            var data: ByteArray?
            val bytesRead   = audioRecord?.read(frames, 0, datagram_sz)
            Log.d("AudioProcessing", "frames=$frames")
            Log.d("AudioProcessing", "bytesRead=$bytesRead")

            if (frames.size >= datagram_sz) {

                val header_type = client_mid_level_functions.get_header_header_sz(
                    compress_data_pad_str,
                    timestamp_out,
                    audioFormat,
                    channelConfig,
                    after_timestamp_char,
                    data_header_end_str
                )

                val header = header_type.first
                val header_sz = header_type.second

                val msg_sz = datagram_sz - header_sz


                data = ByteArray(msg_sz)
                System.arraycopy(frames, 0, data, 0, msg_sz)
                data = header + data
                Arrays.fill(frames, 0.toByte())


                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName(socket_IP),
                    socket_port
                )
                socket?.send(packet)


                timestamp_out++
            } else {
                Thread.sleep((sleep_fraction * datagram_sz / fs).toLong())
            }
        }
    }


    fun client_compress_s2c(data_s2c: ByteArray, pidges_filled: Boolean) {

        val packet = DatagramPacket(data_s2c, data_s2c.size)
        socket?.receive(packet)
        val numberOfBytes = packet.length
        audioTrack?.write(data_s2c, 0, numberOfBytes)


        audioTrack?.play()


       while (data_s2c.sliceArray(0 until 8).decodeToString() != compress_stop_flag) {
            val header_type = client_mid_level_functions.get_header_header_sz_from_datagram(
                data_s2c,
                data_header_end_str
            )

            val header = header_type?.first
            val header_sz = header_type?.second

            Log.d("AudioProcessing", "header-s2c=$header")
            Log.d("AudioProcessing", "header_sz-s2c=$header_sz")
        }
    }


    fun amplitude_audio(bytesRead: Int, frames: ByteArray) {

        var sumAmplitude = 0
        for (i in 0 until bytesRead) {
            val amplitude = frames[i].toInt()
            sumAmplitude += Math.abs(amplitude)
        }

        val averageAmplitude = sumAmplitude.toFloat() / bytesRead

        // Fazer algo com a média das amplitudes, por exemplo, exibir em um log
        Log.d("AudioProcessing", "Amplitude media: $averageAmplitude")
    }


    fun closeAudioSendWire() {
        val wirestopMessage = wire_stop_flag.toByteArray(Charsets.UTF_8)
        val sendWerePacket = DatagramPacket(
            wirestopMessage,
            wirestopMessage.size,
            InetAddress.getByName(socket_IP),
            socket_port
        )
        Log.d("AudioProcessing", "sendWerePacket=$sendWerePacket")
        socket?.send(sendWerePacket)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        socket?.close()
        socket = null
    }

    fun closeAudioSendCompress() {
        val wirestopMessage = compress_stop_flag.toByteArray(Charsets.UTF_8)
        val sendPmocPacket = DatagramPacket(
            wirestopMessage,
            wirestopMessage.size,
            InetAddress.getByName(socket_IP),
            socket_port
        )
        Log.d("AudioProcessing", "sendWerePacket=$sendPmocPacket")
        socket?.send(sendPmocPacket)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        socket?.close()
        socket = null
    }
}