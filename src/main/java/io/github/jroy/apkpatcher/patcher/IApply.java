package io.github.jroy.apkpatcher.patcher;

import java.io.File;

public interface IApply {
  boolean apply(File file);

  String getName();
}
