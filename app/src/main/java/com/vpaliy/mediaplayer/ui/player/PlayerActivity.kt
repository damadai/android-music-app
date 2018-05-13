package com.vpaliy.mediaplayer.ui.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.AppCompatActivity
import android.text.format.DateUtils
import android.view.View
import android.widget.SeekBar
import com.google.gson.reflect.TypeToken
import com.vpaliy.mediaplayer.R
import com.vpaliy.mediaplayer.domain.playback.QueueManager
import com.vpaliy.mediaplayer.playback.PlaybackManager
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import com.vpaliy.mediaplayer.playback.MusicPlaybackService
import kotlinx.android.synthetic.main.activity_player.*
import java.util.concurrent.TimeUnit
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import butterknife.ButterKnife
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.vpaliy.mediaplayer.ui.base.Navigator
import butterknife.OnClick
import com.vpaliy.mediaplayer.ui.utils.*
import org.koin.android.ext.android.inject

class PlayerActivity : AppCompatActivity() {

  private val executorService = Executors.newSingleThreadScheduledExecutor()
  private var scheduledFuture: ScheduledFuture<*>? = null
  private val handler = Handler()
  private var lastState: PlaybackStateCompat? = null
  private var queue: QueueManager? = null

  companion object {
    const val PROGRESS_UPDATE_INTERNAL: Long = 100
    const val PROGRESS_UPDATE_INITIAL_INTERVAL: Long = 10
  }

  private val navigator: Navigator by inject()

  private val controllerCallback = object : MediaControllerCompat.Callback() {
    override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
      updatePlaybackState(state)
    }

    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
      updateDuration(metadata)
      updatePicture(metadata)
    }
  }

  private val connectionCallback: MediaBrowserCompat.ConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
    override fun onConnected() {
      super.onConnected()
      try {
        val mediaController = MediaControllerCompat(this@PlayerActivity, browser?.sessionToken!!)
        mediaController.registerCallback(controllerCallback)
        MediaControllerCompat.setMediaController(this@PlayerActivity, mediaController)
      } catch (ex: RemoteException) {
        ex.printStackTrace()
      }
    }
  }

  private var browser: MediaBrowserCompat? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_player)
    ButterKnife.bind(this)
    browser = MediaBrowserCompat(this,
        ComponentName(this, MusicPlaybackService::class.java),
        connectionCallback, null)
    progressView.progressDrawable?.setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY)
    progressView.thumb?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
    progressView.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onStopTrackingTouch(seekBar: SeekBar?) {
        seekBar?.let {
          MediaControllerCompat.getMediaController(this@PlayerActivity).transportControls
              .seekTo(it.progress.toLong())
          startSeekBarUpdate()
        }
      }

      override fun onStartTrackingTouch(seekBar: SeekBar?) = stopSeekBarUpdate()
      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        if (seekBar.max >= progress) {
          start_time.text = DateUtils.formatElapsedTime((progress / 1000).toLong())
        } else stopSeekBarUpdate()
      }
    })
    shuffle.tag = false
    repeat.tag = false
  }


  @OnClick(R.id.next)
  fun next() = controls().skipToNext()

  @OnClick(R.id.prev)
  fun prev() = controls().skipToPrevious()

  @OnClick(R.id.repeat)
  fun repeat() {
    if (repeat.tag != null)
      controls().setRepeatMode(0)
  }

  @OnClick(R.id.shuffle)
  fun shuffle() {
    if (shuffle != null)
      controls().setShuffleModeEnabled(true)
  }

  @OnClick(R.id.play_pause)
  fun playPause() {
    lastState = null
    val controllerCompat = MediaControllerCompat.getMediaController(this)
    val stateCompat = controllerCompat.playbackState
    if (stateCompat != null) {
      val controls = controllerCompat.transportControls
      when (stateCompat.state) {
        PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.STATE_BUFFERING -> controls.pause()
        PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.STATE_STOPPED -> controls.play()
      }
    }
  }

  @OnClick(R.id.shuffled_list)
  fun additional() {
    queue?.let {
      navigator.actions(this, Bundle().packHeavyObject(Constants.EXTRA_TRACK, it.current()))
    }
  }

  override fun onStart() {
    super.onStart()
    browser?.connect()
  }

  override fun onStop() {
    super.onStop()
    browser?.disconnect()
    MediaControllerCompat.getMediaController(this)
        .unregisterCallback(controllerCallback)
  }

  private fun controls() = MediaControllerCompat.getMediaController(this).transportControls

  private fun stopSeekBarUpdate() {
    lastState = null
    scheduledFuture?.cancel(true)
  }

  private fun startSeekBarUpdate() {
    scheduledFuture = executorService.scheduleAtFixedRate({ handler.post(this@PlayerActivity::updateProgress) },
        PROGRESS_UPDATE_INITIAL_INTERVAL, PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS)
  }

  private fun updateProgress() {
    lastState?.let {
      var position = it.position
      if (it.state == PlaybackStateCompat.STATE_PLAYING) {
        val timeDelta = SystemClock.elapsedRealtime() - it.lastPositionUpdateTime
        position += (timeDelta.toInt() * it.playbackSpeed).toLong()
        if (position <= progressView.max) {
          progressView.progress = position.toInt()
          start_time.text = DateUtils.formatElapsedTime(position / 1000)
        }
      }
    }
  }

  public override fun onDestroy() {
    super.onDestroy()
    stopSeekBarUpdate()
    executorService.shutdown()
  }

  private fun updatePlaybackState(stateCompat: PlaybackStateCompat?) {
    stateCompat?.let {
      lastState = stateCompat
      updateMode(repeat, (stateCompat.actions.toInt()
          and PlaybackStateCompat.ACTION_SET_REPEAT_MODE.toInt()) != 0)
      updateMode(shuffle, (stateCompat.actions.toInt()
          and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED.toInt()) != 0)
      when (stateCompat.state) {
        PlaybackStateCompat.STATE_PLAYING -> {
          play_pause.visibility = View.VISIBLE
          if (play_pause.isPlay) {
            play_pause.change(false, true)
          }
          startSeekBarUpdate()
        }
        PlaybackStateCompat.STATE_PAUSED -> {
          play_pause.visibility = View.VISIBLE
          if (!play_pause.isPlay) {
            play_pause.change(true, true)
          }
          stopSeekBarUpdate()
        }
        PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {
          play_pause.visibility = View.VISIBLE
          if (play_pause.isPlay) {
            play_pause.change(false, true)
          }
          stopSeekBarUpdate()
        }
        PlaybackStateCompat.STATE_BUFFERING -> {
          play_pause.visibility = View.INVISIBLE
          stopSeekBarUpdate()
        }
      }
    }
  }

  private fun updateMode(target: ImageView, isEnabled: Boolean) {
    if (target.tag != isEnabled) {
      target.tag = null
      target.animate().scaleY(0f)
          .scaleX(0f)
          .setDuration(100)
          .setInterpolator(OvershootInterpolator())
          .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
              super.onAnimationEnd(animation)
              setDrawableColor(target, ContextCompat.getColor(this@PlayerActivity,
                  if (isEnabled) R.color.enabled_action else R.color.white_50))
              target.animate().scaleX(1f)
                  .scaleY(1f)
                  .setDuration(100)
                  .setListener(null).start()
              target.tag = isEnabled

            }
          }).start()
    }
  }

  private fun setDrawableColor(imageView: ImageView, color: Int) {
    imageView.drawable?.let {
      DrawableCompat.setTint(it, color)
    }
  }

  private fun updateDuration(metadataCompat: MediaMetadataCompat?) {
    metadataCompat?.let {
      var duration = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER).toInt()
      start_time.text = DateUtils.formatElapsedTime((duration / 1000).toLong())
      duration = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).toInt()
      end_time.text = DateUtils.formatElapsedTime((duration / 1000).toLong())
      progressView.max = duration
    }
  }

  private fun updatePicture(metadataCompat: MediaMetadataCompat?) {
    metadataCompat?.let {
      val number = metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER)
      val text = number.toString() + getString(R.string.of_label) + metadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS).toString()
      val imageUrl = metadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
      track_name.text = metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
      artist.text = metadataCompat.getText(MediaMetadataCompat.METADATA_KEY_ARTIST)
      pages.text = text
      showArt(imageUrl)
    }
  }

  private fun showArt(artUrl: String?) {
    Glide.with(this)
        .load(artUrl)
        .asBitmap()
        .priority(Priority.IMMEDIATE)
        .into(circle)
  }

  override fun finish() {
    super.finish()
    overridePendingTransition(0, R.anim.slide_out_down)
  }

  fun injectManager(manager: PlaybackManager) {
    if (intent == null || intent.extras == null) {
      queue = manager.queueManager
      manager.requestUpdate()
    } else {
      queue = intent.extras.fetchHeavyObject<QueueManager>(Constants.EXTRA_QUEUE,
          object : TypeToken<QueueManager>() {}.type)
      queue?.let {
        manager.queueManager = it
        manager.handleResumeRequest()
      }
      intent.removeExtra(Constants.EXTRA_QUEUE)
    }
  }
}
