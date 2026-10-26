# YAMNet and LiteRT provenance

- Model: Google `google/yamnet/tflite/classification-tflite/1`, instance 633, version 1.
- Official model page: https://www.kaggle.com/models/google/yamnet/tfLite/classification-tflite/1
- Bundled model: `app/src/main/assets/yamnet.tflite`, 4,126,810 bytes.
- SHA-256: `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`.
- Model publisher declares Apache 2.0; `tensorflow-models-LICENSE.txt` and the official model metadata are retained here. The upstream model archive itself has no separate LICENSE/NOTICE file.
- Labels: 521 entries extracted from the model's associated file, bundled as `yamnet_label_list.txt`; key indices and list size are validated at load time.
- Index reference: POC-verified `yamnet_class_map.csv` from the official TensorFlow Models AudioSet/YAMNet source.
- Input/output: mono float32 waveform, 16 kHz, 15,600 samples; float32 scores `[1, 521]`.
- Runtime: `com.google.ai.edge.litert:litert:1.4.2` plus `litert-api:1.4.2`; original publisher license files are retained here.

The POC's threshold starting points are retained, but are uncalibrated; YAMNet scores are not calibrated probabilities. Low-volume dog vocalizations can be mislabeled as cat sounds. Test with labeled device recordings and calibrate by device, microphone, distance, and environment before making product accuracy claims.

## Release review still required

The upstream POC records that the full static/native third-party component inventory inside the LiteRT prebuilt JNI libraries has not been completely traced. In particular, review XNNPACK, FlatBuffers, ruy, Eigen, backend helper components, source revisions, and any MPL obligations before commercial distribution. The Apache notices in this folder do not complete that native dependency audit or independently establish all commercial release obligations.
