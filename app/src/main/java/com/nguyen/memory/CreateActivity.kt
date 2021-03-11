package com.nguyen.memory

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.nguyen.memory.databinding.ActivityCreateBinding
import drawable.EXTRA_BOARD_SIZE
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        const val TAG = "CreateActivity"
        const val RC_PICK_PHOTO = 1600
        const val RC_READ_EXTERNAL_STORAGE = 1776
        const val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE
        const val GAME_NAME_LENGTH_MIN = 3
        const val GAME_NAME_LENGTH_MAX = 14
    }

    lateinit var binding: ActivityCreateBinding
    lateinit var boardSize: BoardSize
    lateinit var adapter: ImagePickerAdapter
    var numImagesRequired = -1
    val selectedUris = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_create)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // show back button in action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // change action bar title according to the extra board size received
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / $numImagesRequired)"

        binding.btnSave.setOnClickListener {
            saveToFirebase()
        }

        // restrict the game name to at most 14 characters
        binding.etGameName.filters = arrayOf(InputFilter.LengthFilter(GAME_NAME_LENGTH_MAX))
        binding.etGameName.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun afterTextChanged(p0: Editable?) {
                binding.btnSave.isEnabled = shouldEnableSave()
            }
        })

        // set up RecyclerView
        binding.rvImagePicker.setHasFixedSize(true)
        binding.rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())
        adapter = ImagePickerAdapter(this, selectedUris, boardSize, object: ImagePickerAdapter.ImageClickListener {
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
                    if (selectedUris.size < numImagesRequired) {
                        selectedUris.add(clipItem.uri)
                    }
                }
            } else if (selectedUri != null) {
                Log.i(TAG, "data: $selectedUri")
                selectedUris.add(selectedUri)
            }
            adapter.notifyDataSetChanged()

            supportActionBar?.title = "Choose pics (${selectedUris.size} / $numImagesRequired)"
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

    private fun saveToFirebase() {
        Log.i(TAG, "saveToFirebase")
        for ((i, imageUri) in selectedUris.withIndex()) {
            val imageByteArray = getImageByteArray(imageUri)
        }
    }

    private fun getImageByteArray(imageUri: Uri): ByteArray {
        // extract bitmap depending on which Android version
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        }

        Log.i(TAG, "Original width: ${originalBitmap.width} and height: ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        Log.i(TAG, "Scaled width: ${scaledBitmap.width} and height: ${scaledBitmap.height}")

        val byteArrayStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteArrayStream)
        return byteArrayStream.toByteArray()
    }

    private fun shouldEnableSave(): Boolean {
        // button should be enabled only if the number of selected images equals the number required,
        // and the game name is not blank and is at least 3 characters long
        return selectedUris.size == numImagesRequired && binding.etGameName.text.isNotBlank() && binding.etGameName.text.length >= GAME_NAME_LENGTH_MIN
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