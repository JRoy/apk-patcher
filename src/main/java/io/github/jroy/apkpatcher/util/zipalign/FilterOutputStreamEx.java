package io.github.jroy.apkpatcher.util.zipalign;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FilterOutputStreamEx extends FilterOutputStream {

  private long totalWritten = 0;

  /**
   * Creates new instance.
   *
   * @param out {@link OutputStream}.
   */
  public FilterOutputStreamEx(OutputStream out) {
    super(out);
  }

  @Override
  public void write(byte[] b) throws IOException {
    out.write(b);
    totalWritten += b.length;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    out.write(b, off, len);
    totalWritten += len;
  }

  @Override
  public void write(int b) throws IOException {
    out.write(b);
    totalWritten += 1;
  }

  public void writeInt(long v) throws IOException {
    write((int) ((v) & 0xff));
    write((int) ((v >>> 8) & 0xff));
    write((int) ((v >>> 16) & 0xff));
    write((int) ((v >>> 24) & 0xff));
  }

  public void writeShort(int v) throws IOException {
    write((v) & 0xff);
    write((v >>> 8) & 0xff);
  }

  public long totalWritten() {
    return totalWritten;
  }
}