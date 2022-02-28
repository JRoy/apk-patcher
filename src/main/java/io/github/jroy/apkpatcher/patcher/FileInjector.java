package io.github.jroy.apkpatcher.patcher;

import io.github.jroy.apkpatcher.util.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;

public class FileInjector implements IApply {
  private final String name;
  private final URL resource;
  private final String targetPath;

  public FileInjector(String name, Class<?> resourceClass, String resourcePath, String targetPath) {
    this.name = name;
    URL resource = resourceClass.getResource("/" + resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found for FileInjector " + name + ": " + resourcePath);
    }
    this.resource = resource;
    this.targetPath = targetPath;
  }

  /**
   * @param file The apk output directory.
   */
  @Override
  public boolean apply(File file) {
    //noinspection ConstantConditions - If this is null, something is very wrong.
    final File lastSmaliFolder = Arrays.stream(file.listFiles())
        .filter(f -> f.isDirectory() && (f.getName().equals("smali") || f.getName().startsWith("smali_classes3")))
        .max(Comparator.naturalOrder())
        .orElseThrow();

    File target = new File(lastSmaliFolder, targetPath);
    if (!target.getParentFile().mkdirs()) {
      Logger.error("Failed to create target directory, " + targetPath + ", for FileInjector: " + name);
      return false;
    }

    try {
      FileUtils.copyURLToFile(resource, target);
      return true;
    } catch (IOException e) {
      Logger.error("Error copying resource to target directory, " + targetPath + ", for FileInjector: " + name);
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public String getName() {
    return name;
  }
}
