package com.example.musicplayerapplication;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.bumptech.glide.Glide;
import com.example.musicplayerapplication.adapter.SongAdapter;
import com.example.musicplayerapplication.helper.RecyclerItemTouchHelper;
import com.example.musicplayerapplication.model.Song;
import com.example.musicplayerapplication.service.OnClearFromRecentService;
import com.example.musicplayerapplication.utils.PermissionsUtil;
import com.example.musicplayerapplication.utils.TimeUtil;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Playable,
        RecyclerItemTouchHelper.RecyclerItemTouchHelperListener, SongAdapter.SongAdapterListener,
        View.OnClickListener, MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener, SeekBar.OnSeekBarChangeListener {
        private static Uri sArtworkUri = Uri.parse("content://media/external/audio");
        private final int STORAGE_PERMISSION_ID = 0;
        private List<Song> mSongList = new ArrayList<>();
        private RecyclerView mRecyclerViewSongs;
        private SongAdapter mAdapter;
        private CoordinatorLayout mCoordinatorLayout;
        private LinearLayout mMediaLayout;
        private TextView mTvTitle;
        private ImageView mIvArtwork;
        private ImageView mIvPlay;
        private ImageView mIvPrevious;
        private ImageView mIvNext;
        private boolean isPlaying = false;
        private SeekBar songProgressBar;
        private MediaPlayer mMediaPlayer;
        private TextView mTvCurrentDuration;
        private TextView mTvTotalDuration;
        private TimeUtil timeUtil;
        private int currentSongIndex;

        //private int position = 0;
        private NotificationManager notificationManager;


    // Handler to update UI timer, progress bar etc,.
        private Handler mHandler = new Handler();
        private Runnable mUpdateTimeTask = new Runnable() {
            public void run() {
                if (mMediaPlayer == null) return;
                long totalDuration = mMediaPlayer.getDuration();
                long currentDuration = mMediaPlayer.getCurrentPosition();
                mTvTotalDuration
                        .setText(String.format("%s", timeUtil.milliSecondsToTimer(totalDuration)));
                mTvCurrentDuration
                        .setText(String.format("%s", timeUtil.milliSecondsToTimer(currentDuration)));
                int progress = (timeUtil.getProgressPercentage(currentDuration, totalDuration));
                songProgressBar.setProgress(progress);
                mHandler.postDelayed(this, 100);
            }
        };

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            init();
            setUpAdapter();
            setUpListeners();
            getSongList();

            showOnNotification();
        }

        private void init() {
            if (!checkStorePermission(STORAGE_PERMISSION_ID)) {
                showRequestPermission(STORAGE_PERMISSION_ID);
            }
            mMediaPlayer = new MediaPlayer();
            timeUtil = new TimeUtil();
            mRecyclerViewSongs = findViewById(R.id.recycler_view);
            mCoordinatorLayout = findViewById(R.id.coordinator_layout);
            mMediaLayout = findViewById(R.id.layout_media);
            mIvArtwork = findViewById(R.id.iv_artwork);
            mIvPlay = findViewById(R.id.iv_play);
            mIvPrevious = findViewById(R.id.iv_previous);
            mIvNext = findViewById(R.id.iv_next);
            mTvTitle = findViewById(R.id.tv_title);
            mTvCurrentDuration = findViewById(R.id.songCurrentDurationLabel);
            mTvTotalDuration = findViewById(R.id.songTotalDurationLabel);
            songProgressBar = findViewById(R.id.songProgressBar);
        }

        private void setUpAdapter() {
            mAdapter = new SongAdapter(getApplicationContext(), mSongList, this);
            RecyclerView.LayoutManager mLayoutManager =
                    new LinearLayoutManager(getApplicationContext());
            mRecyclerViewSongs.setLayoutManager(mLayoutManager);
            mRecyclerViewSongs.setItemAnimator(new DefaultItemAnimator());
            mRecyclerViewSongs.setAdapter(mAdapter);
        }

        private void setUpListeners() {
            ItemTouchHelper.SimpleCallback itemTouchHelperCallback =
                    new RecyclerItemTouchHelper(0, ItemTouchHelper.LEFT, this);
            new ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(mRecyclerViewSongs);
            mIvPlay.setOnClickListener(this);
            mIvPrevious.setOnClickListener(this);
            mIvNext.setOnClickListener(this);
            songProgressBar.setOnSeekBarChangeListener(this);
            mMediaPlayer.setOnCompletionListener(this);


        }

        public void getSongList() {
            //retrieve item_song info
            ContentResolver musicResolver = getContentResolver();
            Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
            if (musicCursor != null && musicCursor.moveToFirst()) {
                //get columns
                int titleColumn = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.TITLE);
                int idColumn = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media._ID);
                int albumID = musicCursor.getColumnIndex
                        (MediaStore.Audio.Media.ALBUM_ID);
                int artistColumn = musicCursor.getColumnIndex
                        (android.provider.MediaStore.Audio.Media.ARTIST);
                int songLink = musicCursor.getColumnIndex
                        (MediaStore.Audio.Media.DATA);
                //add songs to list
                do {
                    long thisId = musicCursor.getLong(idColumn);
                    String thisTitle = musicCursor.getString(titleColumn);
                    String thisArtist = musicCursor.getString(artistColumn);
                    Uri thisSongLink = Uri.parse(musicCursor.getString(songLink));
                    long some = musicCursor.getLong(albumID);
                    Uri uri = ContentUris.withAppendedId(sArtworkUri, some);
                    mSongList.add(new Song(thisId, thisTitle, thisArtist, uri.toString(),
                            thisSongLink.toString()));
                }
                while (musicCursor.moveToNext());
            }
            assert musicCursor != null;
            musicCursor.close();
            // Sort music alphabetically
            Collections.sort(mSongList, new Comparator<Song>() {
                public int compare(Song a, Song b) {
                    return a.getTitle().compareTo(b.getTitle());
                }
            });
            mAdapter.notifyDataSetChanged();


        }

        private boolean checkStorePermission(int permission) {
            if (permission == STORAGE_PERMISSION_ID) {
                return PermissionsUtil.checkPermissions(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                return true;
            }
        }

        private void showRequestPermission(int requestCode) {
            String[] permissions;
            if (requestCode == STORAGE_PERMISSION_ID) {
                permissions = new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };
            } else {
                permissions = new String[]{
                        Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                };
            }
            PermissionsUtil.requestPermissions(this, requestCode, permissions);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == 0) {
                for (int i = 0, len = permissions.length; i < len; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        getSongList();
                        return;
                    }
                }
            }
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction, int position) {
            if (viewHolder instanceof SongAdapter.MyViewHolder) {
                // get the removed item name to display it in snack bar
                String name = mSongList.get(viewHolder.getAdapterPosition()).getTitle();
                // backup of removed item for undo purpose
                final Song deletedItem = mSongList.get(viewHolder.getAdapterPosition());
                final int deletedIndex = viewHolder.getAdapterPosition();
                // remove the item from recycler view
                mAdapter.removeItem(viewHolder.getAdapterPosition());
                //To delete song from device uncomment below code
                // File file = new File(deletedItem.getSongLink());
                // deleteMusic(file);
                // showing snack bar with Undo option
                Snackbar snackbar = Snackbar
                        .make(mCoordinatorLayout, name + " removed from library!", Snackbar.LENGTH_LONG);
                snackbar.setAction("UNDO", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        // undo is selected, restore the deleted item
                        mAdapter.restoreItem(deletedItem, deletedIndex);
                    }
                });
                snackbar.setActionTextColor(Color.YELLOW);
                snackbar.show();
            }
        }

        @Override
        public void onSongSelected(Song song) {
            playSong(song);
            currentSongIndex = mSongList.indexOf(song);

//            String songName = song.getTitle();
//            startActivity(new Intent(getApplicationContext(), PlayerActivity.class)
//                    .putExtra("songs", (Parcelable) mSongList)
//                    .putExtra("songname", songName)
//                    .putExtra("pos", currentSongIndex));
        }

        public boolean deleteMusic(final File file) {
            final String where = MediaStore.MediaColumns.DATA + "=?";
            final String[] selectionArgs = new String[]{
                    file.getAbsolutePath()
            };
            final ContentResolver contentResolver = MainActivity.this.getContentResolver();
            final Uri filesUri = MediaStore.Files.getContentUri("external");
            contentResolver.delete(filesUri, where, selectionArgs);
            if (file.exists()) {
                contentResolver.delete(filesUri, where, selectionArgs);
            }
            return !file.exists();
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.iv_play:
                    playMusic();
                    break;
                case R.id.iv_previous:
                    playPreviousSong();

                    //onTrackPrevious();
                    break;
                case R.id.iv_next:
                    playNextSong();

                    //onTrackNext();
                    break;
                default:
                    break;
            }
        }

        // Change background of play/pause button when music is paused/played
        private void playMusic() {
            if (isPlaying) {
                mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
                isPlaying = false;
                mMediaPlayer.start();
                //onTrackPlay();
                startImageAnimation();
                return;
            }
            mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_play));
            mMediaPlayer.pause();
            isPlaying = true;
            stopImageAnimation();

            //onTrackPause();
        }

        // Play song and update title and thumbnail when click a song on list
        public void playSong(Song song) {
            try {
                mMediaPlayer.reset();
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer
                        .setDataSource(getApplicationContext(), Uri.parse(song.getSongLink()));
                mMediaPlayer.prepare();
                mMediaPlayer.start();
                // Displaying Song title
                isPlaying = true;
                mIvPlay.setBackground(getResources().getDrawable(android.R.drawable.ic_media_pause));
                mMediaLayout.setVisibility(View.VISIBLE);
                mTvTitle.setText(song.getTitle());
                Glide.with(this).load(song.getThumbnail()).placeholder(R.drawable
                        .ic_music).error(R.drawable.ic_music)
                        .crossFade().centerCrop().into(mIvArtwork);
                // set Progress bar values
                songProgressBar.setProgress(0);
                songProgressBar.setMax(100);
                // Updating progress bar
                updateProgressBar();
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                e.printStackTrace();
            }
            resetRotation();
            startImageAnimation();
        }

        // Skip and play the next song
        private void playNextSong() {
            if (currentSongIndex < (mSongList.size() - 1)) {
                Song song = mSongList.get(currentSongIndex + 1);
                playSong(song);
                currentSongIndex = currentSongIndex + 1;
            } else {
                playSong(mSongList.get(0));
                currentSongIndex = 0;
            }
            resetRotation();
            startImageAnimation();
        }

        // Skip and play the previous song
        private void playPreviousSong() {
            if (currentSongIndex > 0) {
                Song song = mSongList.get(currentSongIndex - 1);
                playSong(song);
                currentSongIndex = currentSongIndex - 1;
            } else {
                Song song = mSongList.get(mSongList.size() - 1);
                playSong(song);
                currentSongIndex = mSongList.size() - 1;
            }
            resetRotation();
            startImageAnimation();
        }

        public void updateProgressBar() {
            mHandler.postDelayed(mUpdateTimeTask, 100);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
        }

        // When track finish, it will call the next track
        @Override
        public void onCompletion(MediaPlayer mp) {
            if (currentSongIndex < (mSongList.size() - 1)) {
                playSong(mSongList.get(currentSongIndex + 1));
                currentSongIndex = currentSongIndex + 1;
            } else {
                playSong(mSongList.get(0));
                currentSongIndex = 0;
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        // Override methods of the seekbar
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mHandler.removeCallbacks(mUpdateTimeTask);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mHandler.removeCallbacks(mUpdateTimeTask);
            int totalDuration = mMediaPlayer.getDuration();
            int currentPosition = timeUtil.progressToTimer(seekBar.getProgress(), totalDuration);
            mMediaPlayer.seekTo(currentPosition);
            updateProgressBar();
        }

        // Image rotate circle when song is played
        void startImageAnimation() {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mIvArtwork.animate().rotationBy(360).withEndAction(this).setDuration(10000)
                            .setInterpolator(new LinearInterpolator()).start();
                }
            };
            mIvArtwork.animate().rotationBy(360).withEndAction(runnable).setDuration(10000)
                    .setInterpolator(new LinearInterpolator()).start();
        }

        void stopImageAnimation() {
            mIvArtwork.animate().cancel();
        }

        void resetRotation() { mIvArtwork.setRotation(0);}


    // Music player plays on notification manager bar
    private void showOnNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            createChannel();
            registerReceiver(broadcastReceiver, new IntentFilter("TRACKS_TRACKS"));
            startService(new Intent(getBaseContext(), OnClearFromRecentService.class));
        }
    }

    private void createChannel() {
            // Notification is only on API 26+ because the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(CreateNotification.CHANNEL_ID,
                    "Empty", NotificationManager.IMPORTANCE_DEFAULT);

            notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null){
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    // Create notification service for music player
    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getExtras().getString("actionname");

            switch (action){
                case CreateNotification.ACTION_PREVIUOS:
                    onTrackPrevious();
                    break;
                case CreateNotification.ACTION_PLAY:
                    if (isPlaying){
                        onTrackPause();
                    } else {
                        onTrackPlay();
                    }
                    break;
                case CreateNotification.ACTION_NEXT:
                    onTrackNext();
                    break;
            }
        }
    };

    @Override
    public void onTrackPrevious() {

        currentSongIndex--;
        CreateNotification.createNotification(MainActivity.this, mSongList.get(currentSongIndex),
                R.drawable.ic_pause, currentSongIndex, mSongList.size()-1);
        mTvTitle.setText(mSongList.get(currentSongIndex).getTitle());

    }

    @Override
    public void onTrackPlay() {

        CreateNotification.createNotification(MainActivity.this, mSongList.get(currentSongIndex),
                R.drawable.ic_pause, currentSongIndex, mSongList.size()-1);
        mIvPlay.setImageResource(R.drawable.ic_pause);
        mTvTitle.setText(mSongList.get(currentSongIndex).getTitle());
        isPlaying = true;

    }

    @Override
    public void onTrackPause() {

        CreateNotification.createNotification(MainActivity.this, mSongList.get(currentSongIndex),
                R.drawable.ic_play, currentSongIndex, mSongList.size()-1);
        mIvPlay.setImageResource(R.drawable.ic_play);
        mTvTitle.setText(mSongList.get(currentSongIndex).getTitle());
        isPlaying = false;

    }

    @Override
    public void onTrackNext() {

        currentSongIndex++;
        CreateNotification.createNotification(MainActivity.this, mSongList.get(currentSongIndex),
                R.drawable.ic_pause, currentSongIndex, mSongList.size()-1);
        mTvTitle.setText(mSongList.get(currentSongIndex).getTitle());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            notificationManager.cancelAll();
        }

        unregisterReceiver(broadcastReceiver);
    }
}