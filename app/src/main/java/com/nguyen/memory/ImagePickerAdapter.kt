package com.nguyen.memory

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nguyen.memory.databinding.CardImageBinding

class ImagePickerAdapter(val context: Context, val imageUris: List<Uri>, val boardSize: BoardSize,
                         val imageClickListener: ImageClickListener) : RecyclerView.Adapter<ImagePickerAdapter.ViewHolder>() {

    interface ImageClickListener {
        fun onPlaceholderClicked()
    }

    inner class ViewHolder(val binding: CardImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri?=null) {
            if (uri != null) {
                binding.ivCustomImage.apply {
                    setImageURI(uri)
                    setOnClickListener(null)
                }
            } else {
                binding.ivCustomImage.setOnClickListener {
                    imageClickListener.onPlaceholderClicked()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = CardImageBinding.inflate(inflater)
        binding.ivCustomImage.layoutParams.apply {
            val cardWidth = parent.width / boardSize.getWidth()
            val cardHeight = parent.height / boardSize.getHeight()
            val cardSide = minOf(cardWidth, cardHeight)

            width = cardSide
            height = cardSide
        }

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position < imageUris.size) {
            holder.bind(imageUris[position])
        } else {
            holder.bind()
        }
    }

    override fun getItemCount() = boardSize.getNumPairs()
}
