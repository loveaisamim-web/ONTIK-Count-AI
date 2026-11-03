# ONTIK Count AI (v2.0 — Classical OpenCV High-Precision Grain & Seed Counter)

**ONTIK Count AI** is a 100% offline, production-structured Android grain, seed, lentil, bean, and tablet counting application powered by **OpenCV (`org.opencv:opencv:4.9.0`)**, **CameraX (`1280x720` + `Camera2Interop`)**, **4D Kalman Filter + Hungarian Algorithm**, **Jetpack Compose (Material 3 Light/Dark)**, **DataStore**, and **Room Database**.

No TensorFlow Lite model files, no YOLO weights, and no internet connection are required.

---

## Short Tuning Guide (কোন স্লাইডার কখন পরিবর্তন করবেন)

| Symptom / সমস্যা | Which Slider to Change in Settings / সমাধান |
| :--- | :--- |
| **Grains are missed (দানা মিস হচ্ছে / গুনছে না)** | 1. Tap **Auto Calibrate (ক্যালিব্রেট)** on an empty surface first.<br>2. Increase **Sensitivity (সেন্সিটিভিটি)** slider (e.g. from `50%` to `68%`).<br>3. Lower **Minimum Grain Area (সর্বনিম্ন আকার)** if counting tiny seeds like sesame/mustard.<br>4. Increase **Tracking Distance Gate** if grains are falling very fast. |
| **Grains are double-counted (একই দানা দুইবার গুনছে)** | 1. Increase **Line Hysteresis Band (লাইন হিস্টেরেসিস)** slightly (e.g. to `2.5%`).<br>2. Make sure **Count Direction** matches your drop direction (`Top -> Bottom`) rather than `Bidirectional`.<br>3. If touching grains are being over-split, increase **Manual Single-Grain Area** or tap **Calibrate Grain Size**. |
| **Shadows or dust are counted (ছায়া বা ধুলো গুনছে)** | 1. Turn on the **Torch (ফ্ল্যাশলাইট)** icon in the top bar.<br>2. Ensure **Lock Exposure & White Balance** is enabled in Settings.<br>3. Slightly decrease **Sensitivity** or increase **Minimum Grain Area**. |
