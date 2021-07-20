package io.github.jroy.apkpatcher.util;

import io.github.jroy.apkpatcher.patcher.Patch;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileSearcher {
  private final String prioritySearch;
  private final boolean applyPatches;
  private final Patch[] patches;
  private final List<Patch> appliedPatches = new ArrayList<>();
  private final HashSet<Path> exhaustiveSearches = new HashSet<>();

  public FileSearcher(String prioritySearch, boolean applyPatches, Patch[] patches) {
    this.prioritySearch = prioritySearch;
    this.applyPatches = applyPatches;
    this.patches = patches;
  }

  public void crawlSmaliFolder(Path directory) throws IOException {
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

  public void validateExhaustiveSearch() throws IOException {
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
          for (Patch type : patches) {
            if (appliedPatches.contains(type)) {
              continue;
            }
            if (searchFile(path, type)) {
              break;
            }
          }
        }
      }
    }
  }

  private boolean searchFile(Path file, Patch type) throws IOException {
    List<String> list = new ArrayList<>(Arrays.asList(type.getTerms()));
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
    if (list.isEmpty()) {
      Logger.info("Found term occurrences in " + file.getFileName().toString() + " for patch " + type.getName());
      if (applyPatches) {
        if (type.apply(file.toFile())) {
          Logger.info("Successfully Applied Patch: " + type.getName());
          appliedPatches.add(type);
          return true;
        }
        Logger.error("Failed to apply patch: " + type.getName());
      }
      return true;
    }
    return false;
  }
}
