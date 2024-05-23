package com.geeui.face

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.SurfaceView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.geeui.face.databinding.ActivityIjkplayerBinding
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import java.io.File


class IJkplayerActivity : AppCompatActivity() {
private lateinit var binding: ActivityIjkplayerBinding
    var TAG = "IJkplayerActivity"
    private var mSurfaceView: SurfaceView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIjkplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        var file=File("sdcard/assets/video/h0001.mp4")
        val videoUri= Uri.fromFile(file)
        val player = IjkMediaPlayer()

        binding.surfaceView.holder.addCallback(object : Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                player.setDisplay(holder);
                Log.d(TAG, "surfaceCreated: ")
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.d(TAG, "surfaceChanged: ");
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "surfaceDestroyed: ");

            }

        })
        player.setDataSource(this,videoUri)
        player.prepareAsync()
        player.start()
    }

}