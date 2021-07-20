package io.github.jroy.apkpatcher.patcher;

import io.github.jroy.apkpatcher.util.Logger;
import io.github.jroy.apkpatcher.util.SearchNextLineToken;

import java.util.Scanner;

public abstract class PatchAttachPoint {
  private final Patch patch;
  private final String attachmentPoint;
  private final boolean addMatchedLine;
  private boolean applied = false;

  public PatchAttachPoint(Patch patch, String attachmentPoint) {
    this(patch, attachmentPoint, true);
  }

  public PatchAttachPoint(Patch patch, String attachmentPoint, boolean addMatchedLine) {
    this.patch = patch;
    this.attachmentPoint = attachmentPoint;
    this.addMatchedLine = addMatchedLine;
  }

  protected final Boolean attachLine(String matchedLine, Scanner scanner) {
    if (applied) {
      return null;
    }
    applied = true;
    return applyLine(matchedLine, scanner);
  }

  protected abstract boolean applyLine(String matchedLine, Scanner scanner);

  protected final SearchNextLineToken searchOrNextLine(Scanner scanner, String regex) {
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      if (line.matches(regex)) {
        return new SearchNextLineToken(line);
      }
      patch.addPatchedLine(line);
    }
    Logger.error("Unable to complete patch: " + getPatch().getName() + " while searching for `" + regex + "`");
    return null;
  }

  public Patch getPatch() {
    return this.patch;
  }

  public String getAttachmentPoint() {
    return this.attachmentPoint;
  }

  public boolean isAddMatchedLine() {
    return this.addMatchedLine;
  }
}
