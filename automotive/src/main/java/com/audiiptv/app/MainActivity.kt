package com.audiiptv.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.audiiptv.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.URL


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null
    private var allChannels: List<Channel> = listOf()
    private var selectedCategory: String = "All"
    private var searchQuery: String = ""
    private var isFullscreen = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        binding.favToggle.setTextColor(Color.WHITE)
        binding.favToggle.setTextColor(Color.WHITE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.favToggle.buttonTintList = ColorStateList.valueOf(Color.WHITE)
        }
        setContentView(binding.root)
        initializePlayer()

        //resetPlaylists() // ← RESET FIRST
        loadChannels()   // ← THEN LOAD NOTHING

        binding.addPlaylistButton.setOnClickListener {
            showPlaylistManagerDialog()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }


    private fun resumeLastPlayedChannel() {
        val prefs = getSharedPreferences("last_played", MODE_PRIVATE)
        val lastUrl = prefs.getString("last_url", null)
        val lastName = prefs.getString("last_name", null)

        if (!lastUrl.isNullOrEmpty()) {
            playChannel(lastUrl, lastName ?: "")
            Toast.makeText(this, "Resuming: $lastName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializePlayer() {
        val mediaSourceFactory = DefaultMediaSourceFactory(this)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        binding.playerView.player = player

        val playButton = binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play)
        val pauseButton = binding.playerView.findViewById<View>(androidx.media3.ui.R.id.exo_pause)

        playButton?.setOnClickListener {
            if (player?.isPlaying == false) {
                val lastUrl = getSharedPreferences("last_played", MODE_PRIVATE).getString("last_url", null)
                if (!lastUrl.isNullOrEmpty()) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(lastUrl))
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                }
            }
            player?.playWhenReady = true
        }

        pauseButton?.setOnClickListener {
            player?.playWhenReady = false
        }

        // Manual double-tap detection only
        var lastClickTime = 0L
        val doubleTapThreshold = 300L

        binding.playerView.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime <= doubleTapThreshold) {
                // Double tap: Toggle fullscreen
                toggleFullscreenMode()
                lastClickTime = 0L
            } else {
                // Single tap: No action (or you can add action here if you want)
                lastClickTime = currentTime
            }
        }
    }


    private fun toggleFullscreenMode() {
        isFullscreen = !isFullscreen

        val channelPanel = binding.rootLayout.getChildAt(0) // Left side (channel list)
        val playerPanel = binding.rootLayout.getChildAt(1) // Right side (player)

        if (isFullscreen) {
            // Hide channel list (left panel)
            channelPanel.visibility = View.GONE

            // Expand video panel to full width
            val params = playerPanel.layoutParams as LinearLayout.LayoutParams
            params.weight = 1f
            playerPanel.layoutParams = params

        } else {
            // Show channel list again
            channelPanel.visibility = View.VISIBLE

            // Restore equal split
            val paramsChannel = channelPanel.layoutParams as LinearLayout.LayoutParams
            val paramsPlayer = playerPanel.layoutParams as LinearLayout.LayoutParams

            paramsChannel.weight = 1f
            paramsPlayer.weight = 1f

            channelPanel.layoutParams = paramsChannel
            playerPanel.layoutParams = paramsPlayer
        }
    }

    private fun getCacheFileForPlaylist(playlistName: String): File {
        val safeName = playlistName.replace("[^a-zA-Z0-9_\\-]".toRegex(), "_")
        return File(filesDir, "$safeName.m3u")
    }

    private fun saveM3UToCache(playlistName: String, content: String) {
        val file = getCacheFileForPlaylist(playlistName)
        file.writeText(content)
    }

    private fun loadM3UFromCache(playlistName: String): String? {
        val file = getCacheFileForPlaylist(playlistName)
        return if (file.exists()) file.readText() else null
    }

    private fun deleteM3UCache(playlistName: String) {
        val file = getCacheFileForPlaylist(playlistName)
        if (file.exists()) file.delete()
    }


    private fun loadChannels(autoplayFirst: Boolean = false) {
        // ✅ Show spinner right away
        runOnUiThread {
            binding.loadingOverlay.visibility = View.VISIBLE
        }

        val playlistMap = loadPlaylistsMap()
        var selectedName = getLastSelectedPlaylistName()

        if (selectedName.isEmpty() && playlistMap.isNotEmpty()) {
            selectedName = playlistMap.keys.first()
            saveLastSelectedPlaylistName(selectedName)
        }

        val m3uUrl = playlistMap[selectedName] ?: ""
        if (m3uUrl.isEmpty()) {
            runOnUiThread {
                binding.loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "No playlist selected. Click ➕ to add one.", Toast.LENGTH_LONG).show()
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cachedContent = loadM3UFromCache(selectedName)

                val content = if (cachedContent != null) {
                    cachedContent
                } else {
                    val rawContent = URL(m3uUrl).readText()
                    saveM3UToCache(selectedName, rawContent)
                    rawContent
                }

                val channels = M3UParser.parseFromString(content)

                if (channels.isNullOrEmpty()) {
                    runOnUiThread {
                        binding.loadingOverlay.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Playlist loaded but no channels found.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val favs = getFavoriteUrls()
                channels.forEach { it.isFavorite = favs.contains(it.url) }
                allChannels = channels

                runOnUiThread {
                    setupFavoriteToggle()
                    setupSearchInput()

// Restore last used category for this playlist
                    val lastUsedCategory = getLastUsedCategoryForPlaylist(selectedName)
                    val categories = mutableSetOf("All")
                    allChannels.forEach { categories.add(it.category) }
                    selectedCategory = if (categories.contains(lastUsedCategory)) lastUsedCategory else "All"
                    saveLastUsedCategoryForPlaylist(selectedName, selectedCategory)

// Setup spinner AFTER setting selectedCategory
                    setupCategorySpinner()

// ✅ Force pre-select spinner to match selectedCategory
                    val index = categories.toList().indexOf(selectedCategory)
                    if (index != -1) {
                        binding.categorySpinner.setSelection(index)
                    }

// ✅ Force channel list to show that category
                    setupChannelList(binding.favToggle.isChecked)


// Auto-select last played channel (if any)
                    val lastPlayedUrl = getLastPlayedChannelUrlForPlaylist(selectedName)
                    val channelToPlay = if (autoplayFirst && lastPlayedUrl != null) {
                        allChannels.find { it.url == lastPlayedUrl }
                    } else if (autoplayFirst) {
                        allChannels.firstOrNull()
                    } else null

                    channelToPlay?.let {
                        playChannel(it.url, it.name)
                    }


                    // ✅ Hide spinner AFTER UI is ready
                    binding.loadingOverlay.visibility = View.GONE
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Failed to load playlist: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun savePlaylists(playlists: Set<String>) {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        prefs.edit().putStringSet("all_urls", playlists).apply()
    }

    private fun loadPlaylists(): MutableSet<String> {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        return prefs.getStringSet("all_urls", mutableSetOf()) ?: mutableSetOf()
    }

    private fun saveLastSelectedPlaylistName(name: String) {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        prefs.edit().putString("selected_name", name).apply()
    }

    private fun getLastSelectedPlaylistName(): String {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        return prefs.getString("selected_name", "") ?: ""
    }


    private fun saveLastUsedCategoryForPlaylist(playlistName: String, category: String) {
        val prefs = getSharedPreferences("playlist_meta", MODE_PRIVATE)
        prefs.edit().putString("category_$playlistName", category).apply()
    }

    private fun getLastUsedCategoryForPlaylist(playlistName: String): String {
        val prefs = getSharedPreferences("playlist_meta", MODE_PRIVATE)
        return prefs.getString("category_$playlistName", "All") ?: "All"
    }

    private fun saveLastPlayedChannelUrlForPlaylist(playlistName: String, url: String) {
        val prefs = getSharedPreferences("playlist_meta", MODE_PRIVATE)
        prefs.edit().putString("channel_$playlistName", url).apply()
    }

    private fun getLastPlayedChannelUrlForPlaylist(playlistName: String): String? {
        val prefs = getSharedPreferences("playlist_meta", MODE_PRIVATE)
        return prefs.getString("channel_$playlistName", null)
    }



    private fun setupSearchInput() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s.toString()
                setupChannelList(binding.favToggle.isChecked)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }


    private fun savePlaylistsMap(playlists: Map<String, String>) {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        val json = JSONObject(playlists).toString()
        prefs.edit().putString("playlist_map", json).apply()
    }

    private fun loadPlaylistsMap(): MutableMap<String, String> {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        val jsonStr = prefs.getString("playlist_map", "{}") ?: "{}"
        val json = JSONObject(jsonStr)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            map[key] = json.getString(key)
        }
        return map
    }




    private fun setupCategorySpinner() {
        val categories = mutableSetOf("All")
        allChannels.forEach { categories.add(it.category) }
        val categoryList = categories.toList()

        val adapter = ArrayAdapter(this, R.layout.spinner_item, categoryList)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.categorySpinner.adapter = adapter

        // Preselect last-used category (updated here!)
        val playlistName = getLastSelectedPlaylistName()
        val lastUsedCategory = getLastUsedCategoryForPlaylist(playlistName)
        selectedCategory = if (categoryList.contains(lastUsedCategory)) lastUsedCategory else "All"
        saveLastUsedCategoryForPlaylist(playlistName, selectedCategory)

        val index = categoryList.indexOf(selectedCategory)
        if (index != -1) {
            binding.categorySpinner.setSelection(index, false) // avoid triggering twice
        }

        binding.categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newCategory = categoryList[position]
                if (selectedCategory != newCategory) {
                    selectedCategory = newCategory
                    saveLastUsedCategoryForPlaylist(playlistName, selectedCategory)
                    setupChannelList(binding.favToggle.isChecked)
                }
                (view as? TextView)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                (view as? TextView)?.setTextColor(Color.WHITE)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ✅ Important: refresh list now!
        setupChannelList(binding.favToggle.isChecked)
    }



    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
    }


    private fun setupChannelList(showFavoritesOnly: Boolean = false) {
        var filtered = allChannels

        if (selectedCategory != "All") {
            filtered = filtered.filter { it.category == selectedCategory }
        }

        if (showFavoritesOnly) {
            filtered = filtered.filter { it.isFavorite }
        }

        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = ChannelAdapter(
            filtered,
            onChannelClick = { channel -> playChannel(channel.url, channel.name) },
            onFavoriteToggle = { channel ->
                channel.isFavorite = !channel.isFavorite
                val favs = getFavoriteUrls().toMutableSet()
                if (channel.isFavorite) favs.add(channel.url) else favs.remove(channel.url)
                saveFavoriteUrls(favs)
                setupChannelList(binding.favToggle.isChecked)
            }
        )
    }

    private fun saveFavToggleState(isChecked: Boolean) {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        prefs.edit().putBoolean("fav_toggle", isChecked).apply()
    }

    private fun loadFavToggleState(): Boolean {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        return prefs.getBoolean("fav_toggle", false)
    }


    private fun setupFavoriteToggle() {
        binding.favToggle.setOnCheckedChangeListener { _, isChecked ->
            // Save the state when user toggles
            saveFavToggleState(isChecked)
            setupChannelList(isChecked)
        }

        // Load and apply previously saved state
        val savedState = loadFavToggleState()
        binding.favToggle.isChecked = savedState
        setupChannelList(savedState)
    }


    private fun saveLastSelectedCategory(category: String) {
        val prefs = getSharedPreferences("category", MODE_PRIVATE)
        prefs.edit().putString("last_category", category).apply()
    }

    private fun loadLastSelectedCategory(): String {
        val prefs = getSharedPreferences("category", MODE_PRIVATE)
        return prefs.getString("last_category", "All") ?: "All"
    }


    private fun showPlaylistManagerDialog() {
        val playlistMap = loadPlaylistsMap().toMutableMap()
        val playlistNames = mutableListOf("➕ Create New Playlist")
        playlistNames.addAll(playlistMap.keys)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_playlist_manager, null, false)

        val dialogLoadingOverlay = dialogView.findViewById<FrameLayout>(R.id.dialogLoadingOverlay)
        val dialogLoadingSpinner = dialogView.findViewById<ProgressBar>(R.id.dialogLoadingSpinner)

        val spinner = dialogView.findViewById<Spinner>(R.id.playlistSpinner)
        val inputName = dialogView.findViewById<EditText>(R.id.newPlaylistNameInput)
        val inputUrl = dialogView.findViewById<EditText>(R.id.newPlaylistInput)
        val refreshBtn = dialogView.findViewById<ImageButton>(R.id.refreshPlaylistButton)

        val btnSelect = dialogView.findViewById<Button>(R.id.btnSelect)
        val btnDelete = dialogView.findViewById<Button>(R.id.btnDelete)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, playlistNames)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = adapter

        val lastSelected = getLastSelectedPlaylistName()
        val lastIndex = playlistNames.indexOf(lastSelected)
        spinner.setSelection(if (lastIndex != -1) lastIndex else 0)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedName = playlistNames[position]
                if (selectedName == "➕ Create New Playlist") {
                    inputName.setText("")
                    inputUrl.setText("")
                } else {
                    inputName.setText(selectedName)
                    inputUrl.setText(playlistMap[selectedName] ?: "")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val layoutParams = dialog.window?.attributes
            layoutParams?.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams?.horizontalMargin = 0f
            layoutParams?.verticalMargin = 0f
            dialog.window?.attributes = layoutParams

            // ⏳ Refresh button logic
            refreshBtn.setOnClickListener {
                val selectedName = spinner.selectedItem?.toString()
                if (!selectedName.isNullOrBlank() && playlistMap.containsKey(selectedName)) {
                    dialogLoadingOverlay.visibility = View.VISIBLE

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            deleteM3UCache(selectedName)
                            val rawContent = URL(playlistMap[selectedName]).readText()
                            saveM3UToCache(selectedName, rawContent)

                            val channels = M3UParser.parseFromString(rawContent)
                            val favs = getFavoriteUrls()
                            channels.forEach { it.isFavorite = favs.contains(it.url) }
                            allChannels = channels

                            runOnUiThread {
                                saveLastSelectedPlaylistName(selectedName)
                                setupCategorySpinner()
                                setupFavoriteToggle()
                                setupSearchInput()
                                setupChannelList()

                                dialogLoadingOverlay.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Playlist refreshed!", Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                dialogLoadingOverlay.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Failed to refresh playlist: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }

            // Hide keyboard on outside tap
            dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val focusedView = dialog.currentFocus
                    if (focusedView is EditText) {
                        val outRect = Rect()
                        focusedView.getGlobalVisibleRect(outRect)
                        if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            focusedView.clearFocus()
                            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
                        }
                    }
                }
                false
            }
        }

        // Select button logic
        btnSelect.setOnClickListener {
            val selectedName = spinner.selectedItem?.toString()
            val updatedMap = loadPlaylistsMap()
            if (!selectedName.isNullOrBlank() && updatedMap.containsKey(selectedName)) {
                saveLastSelectedPlaylistName(selectedName)
                loadChannels(autoplayFirst = true)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Playlist not found", Toast.LENGTH_SHORT).show()
            }
        }

        // Delete button logic
        btnDelete.setOnClickListener {
            val toDelete = spinner.selectedItem?.toString()
            if (!toDelete.isNullOrBlank()) {
                playlistMap.remove(toDelete)
                savePlaylistsMap(playlistMap)
                if (getLastSelectedPlaylistName() == toDelete) {
                    saveLastSelectedPlaylistName("")
                }
                Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        // Save / Update button logic
        btnSave.setOnClickListener {
            val newName = inputName.text.toString().trim()
            val newUrl = inputUrl.text.toString().trim()
            if (newName.isNotEmpty() && newUrl.isNotEmpty()) {
                val isUpdating = playlistMap.containsKey(newName)
                playlistMap[newName] = newUrl
                savePlaylistsMap(playlistMap)
                saveLastSelectedPlaylistName(newName)
                loadChannels(autoplayFirst = true)

                val msg = if (isUpdating) "Playlist updated" else "Playlist added"
                Toast.makeText(this, "$msg: $newName", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Name and URL cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }




    private fun resetPlaylists() {
        // Clear SharedPreferences
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Clear memory cache
        allChannels = listOf()

        // Clear RecyclerView
        binding.channelList.adapter = null

        // Stop playback
        player?.pause()
        player?.clearMediaItems()
        player?.stop()

        Toast.makeText(this, "Playlists reset and player stopped.", Toast.LENGTH_SHORT).show()
    }




    private fun savePlaylistUrl(url: String) {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        prefs.edit().putString("url", url).apply()
    }

    private fun loadPlaylistUrl(): String {
        val prefs = getSharedPreferences("playlist", MODE_PRIVATE)
        return prefs.getString("url", "") ?: ""
    }


    private fun playChannel(url: String, name: String = "") {
        val mediaItem = MediaItem.fromUri(Uri.parse(url))

        // ✅ Fully stop old content before preparing new one
        player?.stop()
        player?.clearMediaItems()

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

        saveLastPlayedChannel(url, name)

        // Also save per-playlist
        val playlistName = getLastSelectedPlaylistName()
        if (playlistName.isNotBlank()) {
            saveLastPlayedChannelUrlForPlaylist(playlistName, url)
        }
    }


    private fun saveLastPlayedChannel(url: String, name: String) {
        val prefs = getSharedPreferences("last_played", MODE_PRIVATE)
        prefs.edit()
            .putString("last_url", url)
            .putString("last_name", name)
            .apply()
    }


    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }

    private fun getFavoriteUrls(): MutableSet<String> {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        return prefs.getStringSet("fav_urls", mutableSetOf()) ?: mutableSetOf()
    }

    private fun saveFavoriteUrls(urls: Set<String>) {
        val prefs = getSharedPreferences("favorites", MODE_PRIVATE)
        prefs.edit().putStringSet("fav_urls", urls).apply()
    }



}
