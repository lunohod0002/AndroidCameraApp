package com.example.vkrandroid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vkrandroid.databinding.ItemGalleryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryAdapter(
    private val items: MutableList<MediaFile>,
    private val onClick: (MediaFile) -> Unit,
    private val onDelete: (MediaFile) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        Glide.with(holder.itemView).load(item.uri).into(holder.binding.thumbnail)
        holder.binding.playIcon.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        val date = Date(item.dateAdded * 1000)
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val typeString = if (item.isVideo) "Видео" else "Фото"
        holder.binding.mediaDescription.text =
            holder.itemView.context.getString(
                R.string.media_description,
                typeString,
                dateFormat.format(date)
            )
        holder.itemView.setOnClickListener { onClick(item) }

        holder.binding.btnDelete.setOnClickListener {
            onDelete(item)
        }
    }

    fun removeItem(item: MediaFile) {
        val position = items.indexOf(item)
        if (position != -1) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    override fun getItemCount() = items.size
}