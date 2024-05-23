package com.geeui.face

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.geeui.face.databinding.ActivityExoplayerBinding
import com.geeui.face.databinding.ActivityMainBinding
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import java.io.File

class ExoplayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExoplayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExoplayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        play()
    }

    fun play() {
        val player = SimpleExoPlayer.Builder(this@ExoplayerActivity).build()
        binding.playerView.player = player

// 设置透明背景
        binding.playerView.background = ColorDrawable(Color.TRANSPARENT)

// 创建 MediaSource
        var file=File("sdcard/assets/video/h0005.mp4")
//        var file=File("sdcard/b.mp4")
       val videoUri= Uri.fromFile(file)
//        val videoUri =  Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.a1);
        val mediaSource =
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this@ExoplayerActivity))
                .createMediaSource(MediaItem.fromUri(videoUri))

// 准备播放器并播放视频

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()

    }

}