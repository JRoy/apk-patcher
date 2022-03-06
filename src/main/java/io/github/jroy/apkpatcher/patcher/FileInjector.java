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

  /**
   * @param file The apk output directory.
   */
  @Override
  public boolean apply(File file) {
    //noinspection ConstantConditions - If this is null, something is very wrong.
    final File lastSmaliFolder = Arrays.stream(file.listFiles())
        .filter(f -> f.isDirectory() && (f.getName().equals("smali") || f.getName().startsWith("smali_classes")))
        .max(Comparator.naturalOrder())
        .orElseThrow();


    File smaliFolder = lastSmaliFolder;
    if (newSmaliFolder) {
      final int nextDex = lastSmaliFolder.getName().length() == 5 ? 2 : Integer.parseInt(lastSmaliFolder.getName().substring(13)) + 1;
      smaliFolder = new File(file, "smali_classes" + nextDex);
      if (!smaliFolder.mkdirs()) {
        Logger.error("Failed to create new smali folder for FileInjector" + name);
        return false;
      }
    }

    File target = new File(smaliFolder, targetPath);

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
