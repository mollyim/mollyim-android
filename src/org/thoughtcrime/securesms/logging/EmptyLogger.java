package org.thoughtcrime.securesms.logging;

public class EmptyLogger extends Log.Logger {

  @Override
  public void v(String tag, String message, Throwable t) {}

  @Override
  public void d(String tag, String message, Throwable t) {}

  @Override
  public void i(String tag, String message, Throwable t) {}

  @Override
  public void w(String tag, String message, Throwable t) {}

  @Override
  public void e(String tag, String message, Throwable t) {}

  @Override
  public void wtf(String tag, String message, Throwable t) {}

  @Override
  public void blockUntilAllWritesFinished() {}

  @Override
  public void clear() {}

  @Override
  public String getLog() {
    return "Logs not available.\n";
  }
}
