package com.google.firebase.ml.vision.face;

public class FirebaseVisionFaceDetectorOptions {
  public static final int FAST = 1;
  public static final int NO_CLASSIFICATIONS = 1;
  public static final int NO_CONTOURS = 1;
  public static final int NO_LANDMARKS = 1;
  public static final int ACCURATE = 1;

  public static class Builder {
    public Builder setPerformanceMode(int performanceMode) {
      return this;
    }

    public Builder setMinFaceSize(float v) {
      return this;
    }

    public Builder setContourMode(int noContours) {
      return this;
    }

    public Builder setLandmarkMode(int noLandmarks) {
      return this;
    }

    public Builder setClassificationMode(int noClassifications) {
      return this;
    }

    public FirebaseVisionFaceDetectorOptions build() {
      return new FirebaseVisionFaceDetectorOptions();
    }
  }
}
