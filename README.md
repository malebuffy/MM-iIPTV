# 🎬 MM-IPTV – Premium Audi-style IPTV App for Android Automotive

Welcome to **MM-IPTV**, a modern, sleek IPTV client designed for Android Automotive environments with an **Audi MMI-inspired UI**. It offers a beautiful media experience, playlist management, and caching, all optimized for sideloaded deployment.

---

## 🚗 Features

- ✅ Audi-style clean and minimal interface
- ✅ ExoPlayer-based video playback
- ✅ Playlist Manager with Add / Edit / Delete support
- ✅ M3U parsing with channel categories, logos, favorites
- ✅ Local caching of playlists (improves performance on large M3U files)
- ✅ Manual refresh for playlists
- ✅ Last selected playlist & category remembered
- ✅ Last played channel resume on startup
- ✅ Search filter + Favorite toggle
- ✅ Fullscreen mode toggle (Double tap)
- ✅ Hourglass loading indicators
- ✅ Voice control planned (future scope)



---


## 🧠 Technical Highlights

- **Language:** Kotlin  
- **Media Engine:** [AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3)  
- **Architecture:** Single-activity with view-binding  
- **UI:** Custom XML layout styled to resemble Audi MMI aesthetics  
- **Playlist Format:** M3U / M3U8  
- **Caching:** Internal storage-based per playlist  

---

## ⚙️ Installation

Since this app is designed for Android Automotive systems and **does not use Android Auto APIs**, you must **sideload it manually**:

1. Clone this repo:
   ```bash
   git clone https://github.com/yourname/mm-iptv.git
   ```
2. Open in **Android Studio Arctic Fox or newer**
3. Build and run on an Automotive emulator or physical unit
4. Alternatively, generate an APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📥 Requirements

- Android Automotive (API 29+ recommended)
- Internet connection to load M3U playlists
- M3U playlist URL

---

## 🚫 Disclaimer

- This app **is not Google Play Car App Library-compliant**.
- It is meant for **private sideloaded use** on compatible head units.
- Logos and media content are retrieved from M3U files, **no content is hosted or provided by this app**.

---

## 📌 TODO / Roadmap

- [ ] Voice control support (hands-free actions)
- [ ] Android Auto / Car App Library variant
- [ ] EPG integration
- [ ] Pin favorites to top
- [ ] Better logo scaling & fallback
- [ ] Improved error handling & retry mechanism

---

## 📄 License

MIT License – Feel free to use, modify, and share.

---

## 💬 Credits

- ExoPlayer - [AndroidX Media3](https://developer.android.com/media/media3)
- UI inspired by Audi MMI systems
- Icons from [Material Icons](https://fonts.google.com/icons)

---

## ✨ Maintainer

Built with ❤️ by **malebuffy**  
** [Buy me a coffee!]**(https://www.paypal.com/paypalme/vantoniadis)
