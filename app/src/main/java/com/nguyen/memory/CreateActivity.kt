package com.nguyen.memory

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.nguyen.memory.databinding.ActivityCreateBinding
import drawable.EXTRA_BOARD_SIZE
import drawable.EXTRA_GAME_NAME
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

    val storage = Firebase.storage
    val firestore = Firebase.firestore

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

        hideKeyboard()

        // restrict the game name to at most 14 characters
        binding.etGameName.filters = arrayOf(InputFilter.LengthFilter(GAME_NAME_LENGTH_MAX))
        binding.etGameName.addTextChangedListener(object : TextWatcher {
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
        adapter = ImagePickerAdapter(
            this,
            selectedUris,
            boardSize,
            object : ImagePickerAdapter.ImageClickListener {
                override fun onPlaceholderClicked() {
                    if (isPermissionGranted(this@CreateActivity, READ_EXTERNAL_STORAGE)) {
                        launchPhotoIntent()
                    } else {
                        requestPermission(
                            this@CreateActivity,
                            READ_EXTERNAL_STORAGE,
                            RC_READ_EXTERNAL_STORAGE
                        )
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
            Log.w(
                TAG,
                "Did not get data back from the launched activity, user likely cancelled flow"
            )
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == RC_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPhotoIntent()
            } else {
                Toast.makeText(
                    this,
                    "In order to create a custom game, you need to provide access to your photos",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    fun hideKeyboard() {
        val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun saveToFirebase() {
        Log.i(TAG, "saveToFirebase")
        binding.btnSave.isEnabled = false

        val gameName = binding.etGameName.text.toString()
        // check that we're not overriding someone else's data
        firestore.collection("games")
            .document(gameName)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.data != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Name taken")
                        .setMessage("A game already exists with the name '$gameName'. Please choose another.")
                        .setPositiveButton("OK", null)
                        .show()
                    binding.btnSave.isEnabled = true
                } else {
                    uploadGame(gameName)
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Encountered error while saving memory game", exception)
                Toast.makeText(
                    this,
                    "Encountered error while saving memory game",
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnSave.isEnabled = true
            }
    }

    private fun uploadGame(gameName: String) {
        binding.pbUploading.visibility = View.VISIBLE

        var isError = false
        val uploadedUrls = mutableListOf<String>()
        for ((index, imageUri) in selectedUris.withIndex()) {
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val imageReference = storage.reference.child(filePath)
            val imageByteArray = getImageByteArray(imageUri)
            imageReference.putBytes(imageByteArray)
                .continueWithTask { task ->
                    Log.i(TAG, "Uploaded bytes: ${task.result?.bytesTransferred}")
                    imageReference.downloadUrl
                }.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        Log.e(TAG, "Exception with Firebase storage", task.exception)
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        isError = true
                    }
                    if (isError) {
                        binding.pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    } else {
                        val downloadUrl = task.result.toString()
                        uploadedUrls.add(downloadUrl)
                        binding.pbUploading.progress = uploadedUrls.size*100 / selectedUris.size
                        Log.i(
                            TAG,
                            "Finished uploading $imageUri, num uploaded ${uploadedUrls.size}"
                        )
                        if (uploadedUrls.size == selectedUris.size) {
                            uploadImages(gameName, uploadedUrls)
                        }
                    }
                }
        }
    }

    private fun uploadImages(gameName: String, imageUrls: List<String>) {
        // TODO: upload this info to Firestore
        firestore.collection("games")
            .document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { task ->
                binding.pbUploading.visibility = View.GONE

                if (!task.isSuccessful) {
                    Log.e(TAG, "Exception with game creation", task.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                } else {
                    Log.i(TAG, "Successfully created game $gameName")
                    AlertDialog.Builder(this)
                        .setTitle("Upload complete! Let's play your game $gameName")
                        .setPositiveButton("OK") { _, _ ->
                            Intent().apply {
                                putExtra(EXTRA_GAME_NAME, gameName)
                                setResult(Activity.RESULT_OK, this)
                            }
                            finish()
                        }.show()
                }
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
        // 2nd param=quality, between 0 (severe downgraded quality) and 100 (no reduction in quality)
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