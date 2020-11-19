package com.google.firebase.ml.vision;

import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

public class FirebaseVision {
  private static final FirebaseVision INSTANCE = new FirebaseVision();

  public static FirebaseVision getInstance() {
    return INSTANCE;
  }

  public FirebaseVisionFaceDetector getVisionFaceDetector(FirebaseVisionFaceDetectorOptions options) {
    return new FirebaseVisionFaceDetector();
  }
}
