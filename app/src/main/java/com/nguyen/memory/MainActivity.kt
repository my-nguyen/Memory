package com.nguyen.memory

import android.animation.ArgbEvaluator
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.nguyen.memory.databinding.ActivityMainBinding
import com.nguyen.memory.databinding.DialogBoardSizeBinding
import drawable.EXTRA_BOARD_SIZE

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val RC_CREATE = 2015
    }

    lateinit var binding: ActivityMainBinding
    lateinit var memoryGame: MemoryGame
    lateinit var adapter: MemoryBoardAdapter
    var boardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // a hack shortcut to creating a new board game
        Intent(this, CreateActivity::class.java).apply {
            putExtra(EXTRA_BOARD_SIZE, BoardSize.MEDIUM)
            startActivity(this)
        }

        setupBoard()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.won()) {
                    // user has made some progress but has not won the game: show an alert dialog
                    // before resetting the board
                    showAlertDialog("Quit your current game?", null, View.OnClickListener {
                        setupBoard()
                    })
                } else {
                    // user has not made any progress or has already won the game: just reset the board
                    setupBoard()
                }
                return true
            }
            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCreationDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCreationDialog() {
        val inflater = LayoutInflater.from(this)
        val binding = DialogBoardSizeBinding.inflate(inflater)
        showAlertDialog("Create your own memory board", binding.root, View.OnClickListener {
            // save the newly selected choice of board size
            val selectedSize = when (binding.radioGroup.checkedRadioButtonId) {
                R.id.rb_easy -> BoardSize.EASY
                R.id.rb_medium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            // navigate to a new activity
            Intent(this, CreateActivity::class.java).apply {
                putExtra(EXTRA_BOARD_SIZE, selectedSize)
                startActivityForResult(this, RC_CREATE)
            }
        })
    }

    private fun showNewSizeDialog() {
        val inflater = LayoutInflater.from(this)
        val binding = DialogBoardSizeBinding.inflate(inflater)

        // fill dialog with the preselected radio button
        when (boardSize) {
            BoardSize.EASY -> binding.radioGroup.check(R.id.rb_easy)
            BoardSize.MEDIUM -> binding.radioGroup.check(R.id.rb_medium)
            BoardSize.HARD -> binding.radioGroup.check(R.id.rb_hard)
        }

        showAlertDialog("Choose new size", binding.root, View.OnClickListener {
            // save the newly selected choice of board size
            boardSize = when (binding.radioGroup.checkedRadioButtonId) {
                R.id.rb_easy -> BoardSize.EASY
                R.id.rb_medium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            setupBoard()
        })
    }

    private fun showAlertDialog(title: String, view: View?, clickListener: View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                clickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        // set up the 2 TextView's at the bottom
        when (boardSize) {
            BoardSize.EASY -> {
                binding.tvNumMoves.text = "Easy: 4 x 2"
                binding.tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                binding.tvNumMoves.text = "Easy: 6 x 3"
                binding.tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                binding.tvNumMoves.text = "Easy: 6 x 4"
                binding.tvNumPairs.text = "Pairs: 0 / 12"
            }
        }

        // set tvNumPairs text color to red at the beginning of the game
        val startColor = ContextCompat.getColor(this, R.color.color_progress_none)
        binding.tvNumPairs.setTextColor(startColor)

        memoryGame = MemoryGame(boardSize)
        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object: MemoryBoardAdapter.CardClickListener {
            override fun onCardClicked(position: Int) {
                flipCard(position)
            }
        })

        binding.rvBoard.adapter = adapter
        binding.rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
        binding.rvBoard.setHasFixedSize(true)
    }

    private fun flipCard(position: Int) {
        // error checking
        if (memoryGame.won()) {
            Snackbar.make(binding.root, "You already won!", Snackbar.LENGTH_LONG).show()
        } else if (memoryGame.cards[position].isFaceUp) {
            Snackbar.make(binding.root, "Invalid move!", Snackbar.LENGTH_SHORT).show()
        } else {
            // actually flip the card over
            if (memoryGame.flipCard(position)) {
                Log.i(TAG, "Found a match! Number of pairs found: ${memoryGame.numPairsFound}")

                // for tvNumPairs text color, use color interpolation to show progress of the number
                // of pairs found, with start color being red and end color being green
                val progress = memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs()
                val startColor = ContextCompat.getColor(this, R.color.color_progress_none)
                val endColor = ContextCompat.getColor(this, R.color.color_progress_full)
                val interpolation = ArgbEvaluator().evaluate(progress, startColor, endColor) as Int
                binding.tvNumPairs.setTextColor(interpolation)

                binding.tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
                if (memoryGame.won()) {
                    Snackbar.make(binding.root, "You won! Congratulations.", Snackbar.LENGTH_LONG).show()
                }
            }
            binding.tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
            adapter.notifyDataSetChanged()
        }
    }
}