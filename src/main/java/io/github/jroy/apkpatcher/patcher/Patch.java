package io.github.jroy.apkpatcher.patcher;

import io.github.jroy.apkpatcher.util.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;

public abstract class Patch {
  private final String name;
  private final String[] terms;
  private final boolean fileNameTerm;
  private final HashSet<PatchAttachPoint> attachmentPoints = new HashSet<>();
  private final List<String> patchedFile = new ArrayList<>();

  private int applied = 0;

  public Patch(String name, String... terms) {
    this(name, false, terms);
  }

  public Patch(String name, boolean fileNameTerm, String... terms) {
    this.name = name;
    this.fileNameTerm = fileNameTerm;
    this.terms = terms;
  }

  public final boolean apply(File file) {
    try (Scanner fileScanner = new Scanner(file)) {
      while (fileScanner.hasNextLine()) {
        String line = fileScanner.nextLine();
        Boolean success = checkAttachmentPoints(line, fileScanner);
        if (success != null && success) {
          continue;
        }
        patchedFile.add(line);
      }
      try (FileWriter writer = new FileWriter(file, false)) {
        for (String line : patchedFile) {
          writer.write(line + System.lineSeparator());
        }
      }
      return applied == attachmentPoints.size();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private Boolean checkAttachmentPoints(String line, Scanner fileScanner) {
    for (PatchAttachPoint attachPoint : attachmentPoints) {
      if (line.matches(attachPoint.getAttachmentPoint())) {
        if (attachPoint.isAddMatchedLine()) {
          addPatchedLine(line);
        }
        Boolean success = attachPoint.attachLine(line, fileScanner);
        if (success != null) {
          applied++;
          if (success) {
            Logger.info("Applied attachment point " + applied + "/" + attachmentPoints.size() + " for " + getName());
            return true;
          } else {
            Logger.error("Error while applying attachment point " + applied + "/" + attachmentPoints.size() + " for " + getName());
            return null;
          }
        }
      }
    }
    return false;
  }

  protected final void addAttachmentPoint(PatchAttachPoint attachPoint) {
    attachmentPoints.add(attachPoint);
  }

  protected final void addPatchedLine(String line) {
    patchedFile.add(line);
  }

  public String getName() {
    return this.name;
  }

  public String[] getTerms() {
    return terms;
  }

  public boolean isFileNameTerm() {
    return fileNameTerm;
  }
}
