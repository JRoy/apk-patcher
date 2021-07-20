package io.github.jroy.apkpatcher.util;

import java.util.Objects;

public record SearchNextLineToken(String matchedLine) {
  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (SearchNextLineToken) obj;
    return Objects.equals(this.matchedLine, that.matchedLine);
  }

  @Override
  public int hashCode() {
    return Objects.hash(matchedLine);
  }

  @Override
  public String toString() {
    return "SearchNextLineToken[" +
        "matchedLine=" + matchedLine + ']';
  }
}
