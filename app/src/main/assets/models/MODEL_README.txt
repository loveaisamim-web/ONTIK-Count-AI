ONTIK Count AI - On-Device Model Directory
===========================================

Place your TensorFlow Lite (.tflite) models inside this folder (`app/src/main/assets/models/`) prior to building, OR import any `.tflite` model at runtime directly inside the app's Settings screen (which works 100% offline thereafter).

Recommended File Names for Bundling in Assets:
1. `ontik_small_objects_yolov8n.tflite`
   - Dedicated small-object YOLOv8n/v11n model trained on seeds, grains, beans, lentils, pills, or pellets (Input: 640x640 RGB float32 normalized [0..1]).
2. `yolov8n_general.tflite`
   - General YOLOv8n or SSD MobileNetV2 model for everyday larger objects.

Why a Dedicated Small-Object Model Matters:
Generic COCO-pretrained models are trained on 80 everyday macro classes (cars, people, cups) and cannot reliably detect tiny 2mm-5mm seeds or grains unless trained on small-object datasets. ONTIK Count AI never fakes detection counts—if no `.tflite` file is present in assets or imported via Settings, the app clearly informs you and allows one-tap offline `.tflite` model import from device storage.
