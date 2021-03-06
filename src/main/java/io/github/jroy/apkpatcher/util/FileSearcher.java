package io.github.jroy.apkpatcher.util;

import io.github.jroy.apkpatcher.ApkPatcherException;
import io.github.jroy.apkpatcher.patcher.FileInjector;
import io.github.jroy.apkpatcher.patcher.IApply;
import io.github.jroy.apkpatcher.patcher.Patch;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

public class FileSearcher {
  private final File outputDirectory;
  private final String prioritySearch;
  private final boolean applyPatches;
  private final IApply[] patches;
  private final List<IApply> appliedPatches = new ArrayList<>();
  private final HashSet<Path> exhaustiveSearches = new HashSet<>();
  private final List<File> smaliToMove = new ArrayList<>();
  private File lastSmaliFolder;

  public FileSearcher(File outputDirectory, String prioritySearch, boolean applyPatches, IApply[] patches) {
    this.outputDirectory = outputDirectory;
    this.prioritySearch = prioritySearch;
    this.applyPatches = applyPatches;
    this.patches = patches;
  }

  public void searchAndApply() throws IOException, ApkPatcherException {
    boolean needNewSmaliFolder = false;
    for (IApply type : patches) {
      if (type.useNewDex()) {
        needNewSmaliFolder = true;
        break;
      }
    }

    //noinspection ConstantConditions - If this is null, something is very wrong.
    lastSmaliFolder = Arrays.stream(outputDirectory.listFiles())
        .filter(f -> f.isDirectory() && (f.getName().equals("smali") || f.getName().startsWith("smali_classes")))
        .max(Comparator.naturalOrder())
        .orElseThrow();

    if (needNewSmaliFolder) {
      final int nextDex = lastSmaliFolder.getName().length() == 5 ? 2 : Integer.parseInt(lastSmaliFolder.getName().substring(13)) + 1;
      final File newFolder = new File(outputDirectory, "smali_classes" + nextDex);
      if (newFolder.mkdirs()) {
        lastSmaliFolder = newFolder;
      } else {
        Logger.error("Failed to create new smali folder '" + newFolder.getName() + "'!");
      }
    }

    executeFileInjectors();
    crawlSmaliFolders();

    if (!smaliToMove.isEmpty()) {
      Logger.info("Moving " + smaliToMove.size() + " files to '" + lastSmaliFolder.getName() + "'...");
      for (File file : smaliToMove) {
        String filePath = file.getPath();
        filePath = filePath.substring(filePath.indexOf(File.separator) + 1);
        filePath = filePath.substring(filePath.indexOf(File.separator) + 1);

        final File newLocation = new File(lastSmaliFolder, filePath);

        if (!newLocation.mkdirs()) {
          Logger.error("Failed to move smali to location '" + newLocation.getPath() + "'!");
          continue;
        }

        Files.move(file.toPath(), newLocation.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private void executeFileInjectors() {
    for (IApply type : patches) {
      if (type instanceof FileInjector) {
        if (type.apply(lastSmaliFolder)) {
          Logger.info("Successfully Executed Injector: " + type.getName());
          appliedPatches.add(type);
          continue;
        }
        Logger.error("Failed to Execute Injector: " + type.getName());
      }
    }
  }

  private void crawlSmaliFolders() throws IOException, ApkPatcherException {
    final File[] smaliFolders = outputDirectory.listFiles(file -> file.getName().contains("smali"));
    if (smaliFolders == null) {
      throw new ApkPatcherException("Could not find smali folders!");
    }

    for (final File smaliFolder : smaliFolders) {
      final Path path = smaliFolder.toPath();
      Logger.info("Searching '" + path.getFileName().toString() + "/' for target files...");
      crawlSmaliFolder(path);
    }

    validateExhaustiveSearch();
  }

  private void crawlSmaliFolder(Path directory) throws IOException {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path path : directoryStream) {
        if (appliedPatches.size() == patches.length) {
          Logger.info("Skipping smali crawl due to all patches being completed...");
          return;
        }

        if (Files.isDirectory(path)) {
          if (prioritySearch != null && !path.getFileName().toString().equals(prioritySearch)) {
            exhaustiveSearches.add(path);
            continue;
          }
          searchDirectory(path);
        }
      }
    }
  }

  private void validateExhaustiveSearch() throws IOException {
    if (appliedPatches.size() == patches.length) {
      exhaustiveSearches.clear();
      return;
    }

    Logger.info("Preforming exhaustive search...");
    for (Path path : exhaustiveSearches) {
      searchDirectory(path);
    }
  }

  private void searchDirectory(Path directory) throws IOException {
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(directory)) {

      for (Path path : directoryStream) {
        if (appliedPatches.size() == patches.length) {
          return;
        }
        if (Files.isDirectory(path)) {
          searchDirectory(path);
          continue;
        }

        if (Files.isRegularFile(path) && Files.isReadable(path)) {
          for (IApply type : patches) {
            if (appliedPatches.contains(type) || type instanceof FileInjector) {
              continue;
            }
            if (searchFile(path, (Patch) type)) {
              break;
            }
          }
        }
      }
    }
  }

  private boolean searchFile(Path file, Patch patch) throws IOException {
    List<String> list = new ArrayList<>(Arrays.asList(patch.getTerms()));

    if (patch.isFileNameTerm()) {
      list.removeIf(term -> file.getFileName().toString().contains(term));
    } else {
      try (Scanner scanner = new Scanner(file)) {
        scanner:
        while (scanner.hasNextLine()) {
          String line = scanner.nextLine();
          for (String curTerm : list) {
            if (line.contains(curTerm)) {
              list.remove(curTerm);
              if (list.isEmpty()) {
                break scanner;
              }
              break;
            }
          }
        }
      }
    }

    if (list.isEmpty()) {
      Logger.info("Found term occurrences in " + file.getFileName().toString() + " for patch " + patch.getName());
      if (applyPatches) {
        final File patchFile = file.toFile();
        if (patch.apply(patchFile)) {
          Logger.info("Successfully Applied Patch: " + patch.getName());
          appliedPatches.add(patch);

          if (patch.useNewDex()) {
            smaliToMove.add(patchFile);
          }
          return true;
        }
        Logger.error("Failed to apply patch: " + patch.getName());
      }
      return true;
    }
    return false;
  }
}
