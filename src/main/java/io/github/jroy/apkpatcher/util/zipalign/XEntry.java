package io.github.jroy.apkpatcher.util.zipalign;

import java.util.zip.ZipEntry;

public record XEntry(ZipEntry entry, long headerOffset, int flags, int padding) {
}
