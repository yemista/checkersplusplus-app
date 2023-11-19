package com.checkersplusplus.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
        holder.blackIdTextView.text = item.blackId ?: ""
        holder.redIdTextView.text = item.redId ?: ""

        if (item.blackId != null) {
            holder.joinButton.setOnClickListener { onJoinClicked(item.blackId) }
        } else if (item.redId != null) {
            holder.joinButton.setOnClickListener { onJoinClicked(item.redId) }
        }
    }

    override fun getItemCount() = list.size
}
