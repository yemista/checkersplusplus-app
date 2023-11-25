package com.checkersplusplus.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class OpenGameListAdapter(private val list: List<OpenGameListItem>, private val onJoinClicked: (String) -> Unit)
    : RecyclerView.Adapter<OpenGameListAdapter.MyViewHolder>() {

    class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val blackIdTextView: TextView = view.findViewById(R.id.blackIdTextView)
        val redIdTextView: TextView = view.findViewById(R.id.redIdTextView)
        val joinButton: Button = view.findViewById(R.id.joinButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.open_game_item_layout, parent, false)
        return MyViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = list[position]
        holder.blackIdTextView.text = item.blackId ?: "open"
        holder.redIdTextView.text = item.redId ?: "open"
        holder.joinButton.setOnClickListener { onJoinClicked(item.gameId) }
        val context = holder.itemView.context
        if (position % 2 == 0) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.light_grey))
        }
    }

    override fun getItemCount() = list.size
}
