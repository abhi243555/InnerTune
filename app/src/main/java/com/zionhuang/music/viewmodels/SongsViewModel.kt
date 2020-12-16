package com.zionhuang.music.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.View
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.zionhuang.music.db.SongRepository
import com.zionhuang.music.db.entities.ArtistEntity
import com.zionhuang.music.db.entities.ChannelEntity
import com.zionhuang.music.db.entities.Song
import com.zionhuang.music.download.DownloadListener
import com.zionhuang.music.download.DownloadManager
import com.zionhuang.music.download.DownloadService
import com.zionhuang.music.download.DownloadTask
import com.zionhuang.music.extensions.getActivity
import com.zionhuang.music.ui.activities.MainActivity
import com.zionhuang.music.ui.fragments.songs.SongDetailsDialog
import com.zionhuang.music.ui.listeners.SongPopupMenuListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SongsViewModel(application: Application) : AndroidViewModel(application) {
    val songRepository: SongRepository = SongRepository(application)

    val allSongsFlow: Flow<PagingData<Song>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allSongsAsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    val downloadingSongsFlow: Flow<PagingData<Song>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.downloadingSongsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    val allArtistsFlow: Flow<PagingData<ArtistEntity>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allArtistsPagingSource
        }.flow.cachedIn(viewModelScope)
    }

    val allChannelsFlow: Flow<PagingData<ChannelEntity>> by lazy {
        Pager(PagingConfig(pageSize = 50)) {
            songRepository.allChannels
        }.flow.cachedIn(viewModelScope)
    }

    suspend fun getSong(songId: String) = songRepository.getSongById(songId)

    fun getArtistSongsAsFlow(artistId: Int) = Pager(PagingConfig(pageSize = 50)) {
        songRepository.getArtistSongsAsPagingSource(artistId)
    }.flow.cachedIn(viewModelScope)

    val songPopupMenuListener = object : SongPopupMenuListener {
        override fun editSong(songId: String, view: View) {
            (view.context.getActivity() as? MainActivity)?.supportFragmentManager?.beginTransaction()?.apply {
                setTransition(TRANSIT_FRAGMENT_OPEN)
                add(android.R.id.content, SongDetailsDialog(songId))
                addToBackStack(null)
                commit()
            }
        }

        override fun downloadSong(songId: String, context: Context) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = DownloadService.DOWNLOAD_MUSIC_INTENT
                putExtra("task", DownloadTask(id = songId))
            })
        }

        override fun deleteSong(songId: String) {
            viewModelScope.launch {
                songRepository.deleteSongById(songId)
            }
        }
    }

    private val connection = DownloadServiceConnection()
    private var downloadManager: DownloadManager? = null

    private var listeners = mutableListOf<DownloadListener>()
    private val listener: DownloadListener = { task -> listeners.forEach { it(task) } }

    fun addDownloadListener(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun removeDownloadListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    inner class DownloadServiceConnection : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            downloadManager = (service as? DownloadService.DownloadServiceBinder)?.downloadManager
            downloadManager?.addEventListener(listener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadManager?.removeListener(listener)
        }
    }

    init {
        val intent = Intent(application, DownloadService::class.java)
        application.startService(intent)
        application.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
}