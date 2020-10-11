package com.google.firebase.ml.vision.face;

import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import java.io.IOException;
import java.util.List;

public class FirebaseVisionFaceDetector implements AutoCloseable {
  public Task<List<FirebaseVisionFace>> detectInImage (FirebaseVisionImage image) {
    return new Task<>();
  }

  @Override
  public void close() throws InterruptedException, IOException {
  }
}
