package io.github.jroy.apkpatcher;

public class ApkPatcherException extends Exception {
  public ApkPatcherException(String message) {
    super(message);
  }

  public ApkPatcherException(String message, Throwable cause) {
    super(message, cause);
  }
}
