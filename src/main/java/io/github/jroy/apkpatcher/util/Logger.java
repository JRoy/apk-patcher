package io.github.jroy.apkpatcher.util;

public class Logger {
  public static void info(String message) {
    log("[INFO] " + message);
  }

  public static void warn(String message) {
    log("[WARN] " + message);
  }

  public static void error(String message) {
    log("[ERROR] " + message);
  }

  private static void log(String message) {
    System.out.println("[ApkPatcher] " + message);
  }
}
