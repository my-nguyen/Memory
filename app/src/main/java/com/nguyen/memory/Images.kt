package com.nguyen.memory

import com.google.firebase.firestore.PropertyName

data class Images(@PropertyName("images") val images: List<String>?=null)
