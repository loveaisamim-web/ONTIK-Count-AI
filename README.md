# ONTIK Count AI

**ONTIK Count AI** is a complete, production-structured Android application built with **Kotlin**, **Jetpack Compose**, **CameraX**, **TensorFlow Lite (LiteRT)**, and **Room Database** for real-time physical object counting and still-photo object counting.

---

## 1. Key Capabilities

### A. Live Counting Mode (`CameraX` + `MultiObjectTracker` + `LineCrossingEvaluator`)
- **Real-Time CameraX Pipeline**: Uses `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` at 640x640 target resolution with reusable pixel buffers to prevent frame lag and `OutOfMemoryError` on low-RAM Android phones.
- **Configurable Counting Zone & Virtual Counting Line**: Tap the **Crop/Zone icon** on the live camera card to drag the active counting zone corners or reposition the dashed virtual counting line.
- **Velocity-Predictive Multi-Object Tracking**: Assigns a unique, monotonically increasing `trackId` (`#1`, `#2`, ...) to each detected object using combined constant-velocity state prediction + normalized center distance + IoU matching so fast-falling seeds or grains are tracked across frames.
- **Strict Duplicate Prevention**: Every `TrackedObject` carries a persistent `hasBeenCounted` flag and crossing timestamp. An object is counted **if and only if** its tracked center transitions across the virtual counting line in the configured direction (`Top -> Bottom`, `Bottom -> Top`, `Left -> Right`, `Right -> Left`, or `Bidirectional`) and has never been counted before.
- **Controls**: Start, Pause, Resume, Reset, Manual `-1` / `+1` adjustment, Torch/Flash toggle, Front/Rear camera switch, and one-tap session saving.

### B. Photo Counting Mode
- Capture a new photo or pick an existing high-resolution photo from the gallery (automatically EXIF-rotated and safely downsampled).
- Displays AI bounding boxes and center markers with total count.
- **Interactive Manual Correction**:
  - Tap any existing green AI bounding box to suppress a false positive.
  - Tap any unboxed object on the photo to add a blue manual count marker.
  - Use `-1` / `+1` buttons for quick manual adjustment.

### C. AI Model Architecture & Small-Object Support
- Built around `ObjectDetectorContract` and `TFLiteYoloDetector`, supporting:
  1. **YOLOv8 / YOLOv11 TFLite models** (`[1, 4 + numClasses, 8400]`)
  2. **YOLOv5 / Transposed YOLO TFLite models** (`[1, 25200, 5 + numClasses]` or `[1, 8400, 6]`)
  3. **Standard TFLite 4-Tensor Object Detection models** (`locations`, `classes`, `scores`, `numDetections`)
- **Zero Fake Counts Policy**: ONTIK Count AI never fabricates or hardcodes fake detections. If no `.tflite` file is bundled or imported yet, the app displays a clear diagnostic banner explaining how to import your `.tflite` model in **Settings -> Import Custom .tflite Model** or place it in `app/src/main/assets/models/`.
- **100% Offline Operation**: Once a `.tflite` model is bundled in assets or imported via the Settings screen, it is stored in private internal storage (`filesDir/models/`) and runs completely offline using XNNPACK CPU threads.

### D. Session History & CSV / TXT Export
- Backed by **Room Database** (`count_sessions` table).
- Records session date/time, mode (`LIVE` or `PHOTO`), final count, raw AI count, manual delta, counting direction, confidence threshold, duration, and model name.
- Exports full session history to `.csv` and `.txt` via Android `FileProvider` share sheet.

---

## 2. Project Folder Structure

```text
ONTIK-Count-AI/
├── .github/
│   └── workflows/
│       └── android.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/
│           │   └── models/
│           │       ├── MODEL_README.txt
│           │       ├── labels_coco.txt
│           │       └── labels_small_objects.txt
│           ├── java/com/ontik/countai/
│           │   ├── MainActivity.kt
│           │   ├── OntikCountApp.kt
│           │   ├── ai/
│           │   │   ├── LineCrossingEvaluator.kt
│           │   │   ├── MultiObjectTracker.kt
│           │   │   ├── ObjectDetectorContract.kt
│           │   │   └── TFLiteYoloDetector.kt
│           │   ├── data/
│           │   │   ├── local/
│           │   │   │   ├── AppDatabase.kt
│           │   │   │   ├── CountSessionDao.kt
│           │   │   │   └── CountSessionEntity.kt
│           │   │   └── repository/
│           │   │       ├── CountRepository.kt
│           │   │       └── SettingsRepository.kt
│           │   ├── domain/
│           │   │   └── model/
│           │   │       └── CountingModels.kt
│           │   ├── ui/
│           │   │   ├── components/
│           │   │   │   └── CameraPreviewWithZoneOverlay.kt
│           │   │   ├── navigation/
│           │   │   │   └── OntikNavGraph.kt
│           │   │   ├── screens/
│           │   │   │   ├── HistoryScreen.kt
│           │   │   │   ├── LiveCountingScreen.kt
│           │   │   │   ├── PhotoCountingScreen.kt
│           │   │   │   └── SettingsScreen.kt
│           │   │   ├── theme/
│           │   │   │   ├── Color.kt
│           │   │   │   ├── Theme.kt
│           │   │   │   └── Type.kt
│           │   │   └── viewmodel/
│           │   │       └── CountViewModel.kt
│           │   └── util/
│           │       ├── ImageProxyUtils.kt
│           │       └── SessionExportHelper.kt
│           └── res/
│               ├── drawable/
│               │   └── ic_launcher_foreground.xml
│               ├── values/
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── xml/
│                   ├── backup_rules.xml
│                   ├── data_extraction_rules.xml
│                   └── file_paths.xml
├── build.gradle.kts
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts
```

---

## 3. AI Model Integration Procedure (Seeds / Grains / Small Objects)

1. **Train or Export a YOLOv8n / YOLOv11n Small-Object Model to TFLite**:
   ```bash
   pip install ultralytics
   yolo export model=best_small_seeds.pt format=tflite imgsz=640
   ```
2. **Option A — Bundle Inside APK Before Building**:
   Copy your exported `.tflite` file to:
   - `app/src/main/assets/models/ontik_small_objects_yolov8n.tflite` (for the dedicated small-object profile)
   - `app/src/main/assets/models/yolov8n_general.tflite` (for general objects)
3. **Option B — Import Directly Inside the Installed Android App (No Rebuild Needed)**:
   Copy any `.tflite` model to your Android phone's Downloads folder, open **ONTIK Count AI -> Settings -> Import Custom .tflite Model from Phone Storage**, and select the file.

---

## 4. Building via GitHub Actions (No Computer Required) & Termux Upload Commands

Push this project directly from **Termux** on your Android phone to GitHub, and GitHub Actions (`.github/workflows/android.yml`) will automatically compile `app-debug.apk` on `ubuntu-latest` with JDK 17 and Gradle 8.7 and upload it as a downloadable artifact (`ONTIK-Count-AI-Debug-APK`).
