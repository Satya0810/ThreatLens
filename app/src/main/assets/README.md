# Machine Learning Model Directory

Place your custom-trained TensorFlow Lite text classification model here.

**Filename MUST be exactly:** `website_categorizer.tflite`

## Requirements
1. The model must be a valid TensorFlow Lite Task Library `NLClassifier` compatible file.
2. It must be trained to output class labels (e.g., "News", "Adult", "Piracy").
3. The app will automatically read this file at startup. If the file is missing, the ML engine will safely disable itself and fall back to regex heuristics.
