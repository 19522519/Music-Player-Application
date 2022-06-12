package com.example.musicplayerapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.musicplayerapplication.R;
import com.example.musicplayerapplication.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.MyViewHolder> implements
        Filterable {
    private List<Song> mSongList;
    private List<Song> mSongListFiltered;
    private Context mContext;
    private SongAdapterListener listener;

    public SongAdapter(Context context, List<Song> songList, SongAdapterListener listener) {
        this.mContext = context;
        this.listener = listener;
        this.mSongList = songList;
        this.mSongListFiltered = songList;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_song, parent, false);
        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        Song song = mSongListFiltered.get(position);
        holder.title.setText(song.getTitle());
        holder.artist.setText(song.getArtist());
        Glide.with(mContext).load(song.getThumbnail()).placeholder(R.drawable
                .ic_music).error(R.drawable.ic_music)
                .crossFade().centerCrop().into(holder.thumbnail);
    }

    @Override
    public int getItemCount() {
        return mSongListFiltered.size();
    }

    public void removeItem(int position) {
        mSongList.remove(position);
        // notify the item removed by position
        // to perform recycler view delete animations
        // NOTE: don't call notifyDataSetChanged()
        notifyItemRemoved(position);
    }

    public void restoreItem(Song item, int position) {
        mSongList.add(position, item);
        // notify item added by position
        notifyItemInserted(position);
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();
                if (charString.isEmpty()) {
                    mSongListFiltered = mSongList;
                } else {
                    List<Song> filteredList = new ArrayList<>();
                    for (Song row : mSongList) {
                        // name match condition. this might differ depending on your requirement
                        // here we are looking for name or phone number match
                        if (row.getTitle().toLowerCase().contains(charString.toLowerCase()) || row
                                .getArtist().toLowerCase().contains(charSequence)) {
                            filteredList.add(row);
                        }
                    }
                    mSongListFiltered = filteredList;
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = mSongListFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
                mSongListFiltered = (ArrayList<Song>) filterResults.values;
                notifyDataSetChanged();
            }
        };
    }

    public interface SongAdapterListener {
        void onSongSelected(Song song);
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView title, artist;
        public RelativeLayout viewBackground, viewForeground;
        public ImageView thumbnail;

        public MyViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.song_title);
            artist = view.findViewById(R.id.song_artist);
            viewBackground = view.findViewById(R.id.view_background);
            viewForeground = view.findViewById(R.id.view_foreground);
            thumbnail = view.findViewById(R.id.thumbnail);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // send selected contact in callback
                    listener.onSongSelected(mSongListFiltered.get(getAdapterPosition()));
                }
            });
        }
    }
}
