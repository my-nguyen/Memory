package com.nguyen.memory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nguyen.memory.databinding.ActivityMainBinding
import com.nguyen.memory.databinding.DialogBoardSizeBinding
import com.nguyen.memory.databinding.DialogDownloadBoardBinding
import com.squareup.picasso.Picasso
import drawable.EXTRA_BOARD_SIZE
import drawable.EXTRA_GAME_NAME

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val RC_CREATE = 2015
    }

    lateinit var binding: ActivityMainBinding
    lateinit var memoryGame: MemoryGame
    lateinit var adapter: MemoryBoardAdapter

    var boardSize = BoardSize.EASY
    var gameName: String?=null
    var images: List<String>?=null

    val firestore = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*// a hack shortcut to creating a new board game
        Intent(this, CreateActivity::class.java).apply {
            putExtra(EXTRA_BOARD_SIZE, BoardSize.EASY)
            startActivity(this)
        }*/

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
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_CREATE && resultCode == Activity.RESULT_OK) {
            val name = data?.getStringExtra(EXTRA_GAME_NAME)
            if (name == null) {
                Log.e(TAG, "Got null game name from CreateActivity")
            } else {
                downloadGame(name)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showDownloadDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogBinding = DialogDownloadBoardBinding.inflate(inflater)
        showAlertDialog("Fetch memory game", dialogBinding.root, View.OnClickListener {
            // grab the text of the game name that the user wants to download
            val gameToDownload = dialogBinding.etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    private fun downloadGame(name: String) {
        firestore.collection("games")
                .document(name)
                .get()
                .addOnSuccessListener { document ->
                    val downloadedImages = document.toObject(Images::class.java)
                    if (downloadedImages?.images == null) {
                        Log.e(TAG, "Invalid custom game data from Firestore")
                        Snackbar.make(binding.root, "Sorry, we couldn't find any such game '$name", Snackbar.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    } else {
                        val numCards = downloadedImages.images.size * 2
                        boardSize = BoardSize.getByValue(numCards)
                        images = downloadedImages.images
                        // prefetch all images (without showing) to save download time
                        for (imageUrl in downloadedImages.images) {
                            Picasso.get()
                                    .load(imageUrl)
                                    .fetch()
                        }
                        Snackbar.make(binding.root, "You're now playing '$name'", Snackbar.LENGTH_LONG).show()
                        gameName = name
                        setupBoard()
                    }
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Exception when retrieving game", exception)
                }
    }

    private fun showCreationDialog() {
        val inflater = LayoutInflater.from(this)
        val dialogBinding = DialogBoardSizeBinding.inflate(inflater)
        showAlertDialog("Create your own memory board", dialogBinding.root, View.OnClickListener {
            // save the newly selected choice of board size
            val selectedSize = when (dialogBinding.radioGroup.checkedRadioButtonId) {
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
        val dialogBinding = DialogBoardSizeBinding.inflate(inflater)

        // fill dialog with the preselected radio button
        when (boardSize) {
            BoardSize.EASY -> dialogBinding.radioGroup.check(R.id.rb_easy)
            BoardSize.MEDIUM -> dialogBinding.radioGroup.check(R.id.rb_medium)
            BoardSize.HARD -> dialogBinding.radioGroup.check(R.id.rb_hard)
        }

        showAlertDialog("Choose new size", dialogBinding.root, View.OnClickListener {
            // save the newly selected choice of board size
            boardSize = when (dialogBinding.radioGroup.checkedRadioButtonId) {
                R.id.rb_easy -> BoardSize.EASY
                R.id.rb_medium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }

            // reset gameName and images before setupBoard()
            gameName = null
            images = null

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
        // change the title to reflect either the customized game name or the default app name
        supportActionBar?.title = gameName ?: getString(R.string.app_name)

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

        memoryGame = MemoryGame(boardSize, images)
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

                    // show confetti
                    val colors = intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)
                    CommonConfetti.rainingConfetti(binding.root, colors).oneShot()
                }
            }
            binding.tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
            adapter.notifyDataSetChanged()
        }
    }
}