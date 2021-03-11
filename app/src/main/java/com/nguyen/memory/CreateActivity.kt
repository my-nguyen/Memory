package com.nguyen.memory

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.nguyen.memory.databinding.ActivityCreateBinding
import drawable.EXTRA_BOARD_SIZE

class CreateActivity : AppCompatActivity() {

    companion object {
        const val TAG = "CreateActivity"
        const val RC_PICK_PHOTO = 1600
        const val RC_READ_EXTERNAL_STORAGE = 1776
        const val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    lateinit var binding: ActivityCreateBinding
    lateinit var boardSize: BoardSize
    lateinit var adapter: ImagePickerAdapter
    var numImages = -1
    val imageUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_create)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // show back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // change action bar title according to the extra board size received
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImages = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / $numImages)"

        // set up RecyclerView
        binding.rvImagePicker.setHasFixedSize(true)
        binding.rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
        adapter = ImagePickerAdapter(this, imageUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
            override fun onPlaceholderClicked() {
                if (isPermissionGranted(this@CreateActivity, READ_EXTERNAL_STORAGE)) {
                    launchPhotoIntent()
                } else {
                    requestPermission(this@CreateActivity, READ_EXTERNAL_STORAGE, RC_READ_EXTERNAL_STORAGE)
                }
            }
        })
        binding.rvImagePicker.adapter = adapter
    }

    // return to calling Activity when back button is clicked
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_PICK_PHOTO || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity, user likely cancelled flow")
        } else {
            val selectedUri = data.data
            val clipData = data.clipData
            if (clipData != null) {
                Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
                for (i in 0 until clipData.itemCount) {
                    val clipItem = clipData.getItemAt(i)
                    if (imageUris.size < numImages) {
                        imageUris.add(clipItem.uri)
                    }
                }
            } else if (selectedUri != null) {
                Log.i(TAG, "data: $selectedUri")
                imageUris.add(selectedUri)
            }
            adapter.notifyDataSetChanged()

            supportActionBar?.title = "Choose pics (${imageUris.size} / $numImages)"
            binding.btnSave.isEnabled = shouldEnableSave()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == RC_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPhotoIntent()
            } else {
                Toast.makeText(this, "In order to create a custom game, you need to provide access to your photos", Toast.LENGTH_LONG).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun shouldEnableSave(): Boolean {
        // check if we should enable the Save button
        return true
    }

    private fun launchPhotoIntent() {
        Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            val intent = Intent.createChooser(this, "Choose pics")
            startActivityForResult(intent, RC_PICK_PHOTO)
        }
    }
}