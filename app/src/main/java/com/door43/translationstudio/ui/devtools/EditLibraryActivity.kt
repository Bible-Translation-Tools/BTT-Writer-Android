package com.door43.translationstudio.ui.devtools

import android.os.Bundle
import com.door43.translationstudio.databinding.ActivityEditLibraryBinding
import com.door43.translationstudio.ui.BaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditLibraryActivity : BaseActivity() {

    private lateinit var binding: ActivityEditLibraryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
}
