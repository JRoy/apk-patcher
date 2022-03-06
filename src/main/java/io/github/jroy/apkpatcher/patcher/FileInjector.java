package io.github.jroy.apkpatcher.patcher;

import io.github.jroy.apkpatcher.util.Logger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class FileInjector implements IApply {
  private final String name;
  private final URL resource;
  private final String targetPath;
  private final boolean newSmaliFolder;

  public FileInjector(String name, Class<?> resourceClass, String resourcePath, String targetPath, boolean newSmaliFolder) {
    this.name = name;
    URL resource = resourceClass.getResource("/" + resourcePath);
    if (resource == null) {
      throw new IllegalArgumentException("Resource not found for FileInjector " + name + ": " + resourcePath);
    }
    this.resource = resource;
    this.targetPath = targetPath;
    this.newSmaliFolder = newSmaliFolder;
  }

  @Override
  public boolean useNewDex() {
    return newSmaliFolder;
  }

  /**
   * @param file The apk output directory.
   */
  @Override
  public boolean apply(File file) {
    File target = new File(file, targetPath);

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
