package com.audiiptv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ChannelAdapter(
    private val channels: List<Channel>,
    private val onChannelClick: (Channel) -> Unit,
    private val onFavoriteToggle: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val channelName: TextView = view.findViewById(R.id.channelName)
        val favIcon: ImageView = view.findViewById(R.id.favIcon)
        val channelLogo: ImageView = view.findViewById(R.id.channelLogo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]

        holder.channelName.text = channel.name

        Glide.with(holder.itemView.context)
            .load(channel.logo) // optional logo URL
            .placeholder(R.drawable.ic_placeholder_logo)
            .into(holder.channelLogo)

        holder.favIcon.setImageResource(
            if (channel.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline

        )

        holder.itemView.setOnClickListener { onChannelClick(channel) }
        holder.favIcon.setOnClickListener { onFavoriteToggle(channel) }
    }

    override fun getItemCount(): Int = channels.size
}
