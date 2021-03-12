package com.nguyen.memory

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.nguyen.memory.databinding.MemoryCardBinding
import com.squareup.picasso.Picasso

class MemoryBoardAdapter(val context: Context, val boardSize: BoardSize, val cards: List<MemoryCard>,
                         val cardClickListener: CardClickListener) : RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder>() {

    companion object {
        const val TAG = "MemoryBoardAdapter"
        const val MARGIN = 10
    }

    interface CardClickListener {
        fun onCardClicked(position: Int)
    }

    inner class ViewHolder(val binding: MemoryCardBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(position: Int) {
            val card = cards[position]
            if (card.isFaceUp) {
                if (card.imageUrl != null) {
                    Picasso.get()
                        .load(card.imageUrl)
                        .placeholder(R.drawable.ic_image)
                        .into(binding.imageButton)
                } else {
                    binding.imageButton.setImageResource(card.identifier)
                }
            } else {
                binding.imageButton.setImageResource(R.drawable.bamboo)
            }

            // adjust the card opacity and set background color to gray if the card is matched
            val alpha = if (card.isMatched) .4f else 1.0f
            binding.imageButton.alpha = alpha
            val gray = ContextCompat.getColorStateList(context, R.color.color_gray)
            val colorStateList = if (card.isMatched) gray else null
            ViewCompat.setBackgroundTintList(binding.imageButton, colorStateList)

            binding.imageButton.setOnClickListener {
                Log.i(TAG, "Clicked on position $position")
                cardClickListener.onCardClicked(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val binding = MemoryCardBinding.inflate(inflater, parent, false)

        // have each CardView take up all the allocated space
        // parent is the RecyclerView, which is a 4x2 grid
        val cardWidth = parent.width/boardSize.getWidth() - 2*MARGIN
        val cardHeight = parent.height/boardSize.getHeight() - 2*MARGIN
        val cardSide = minOf(cardWidth, cardHeight)
        val layoutParams = binding.cardView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.apply {
            width = cardSide
            height = cardSide
            setMargins(MARGIN, MARGIN, MARGIN, MARGIN)
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount() = boardSize.numCards
}
