package io.github.jroy.apkpatcher.util.zipalign;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class ZipAligner {
  public static final int ZIP_ENTRY_HEADER_LEN = 30;
  public static final int ZIP_ENTRY_VERSION = 20;
  public static final int ZIP_ENTRY_USES_DATA_DESCR = 0x0008;
  public static final int ZIP_ENTRY_DATA_DESCRIPTOR_LEN = 16;
  public static final int DEFAULT_ALIGNMENT = 4;
  public static final int FILE_BUFFER = 32 * 1024;

  private final File mInputFile;
  private final File mOutputFile;
  private final List<XEntry> mXEntries = new ArrayList<>();

  private ZipFile mZipFile;
  private RandomAccessFile mRafInput;
  private FilterOutputStreamEx mOutputStream;
  private long mInputFileOffset = 0;
  private int mTotalPadding = 0;

  public ZipAligner(File input, File output) {
    this.mInputFile = input;
    this.mOutputFile = output;
  }

  public void run() {
    try {
      mZipFile = new ZipFile(mInputFile);
      mRafInput = new RandomAccessFile(mInputFile, "r");
      mOutputStream = new FilterOutputStreamEx(new BufferedOutputStream(
          new FileOutputStream(mOutputFile),
          FILE_BUFFER
      ));
      copyAllEntries();
      buildCentralDirectory();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    } finally {
      IOUtils.closeQuietly(mZipFile);
      IOUtils.closeQuietly(mRafInput);
      IOUtils.closeQuietly(mOutputStream);
    }
  }

  private void copyAllEntries() throws IOException {
    final int entryCount = mZipFile.size();
    if (entryCount == 0) {
      return;
    }

    final Enumeration<? extends ZipEntry> entries = mZipFile.entries();
    while (entries.hasMoreElements()) {
      final ZipEntry entry = entries.nextElement();

      int flags = entry.getMethod() == ZipEntry.STORED ? 0 : 1 << 3;
      flags |= 1 << 11;

      final long outputEntryHeaderOffset = mOutputStream.totalWritten();

      final int inputEntryHeaderSize = ZIP_ENTRY_HEADER_LEN + (entry.getExtra() != null ? entry.getExtra().length : 0) + entry.getName().getBytes(StandardCharsets.UTF_8).length;
      final long inputEntryDataOffset = mInputFileOffset + inputEntryHeaderSize;

      final int padding;

      if (entry.getMethod() != ZipEntry.STORED) {
        padding = 0;
      } else {
        long newOffset = inputEntryDataOffset + mTotalPadding;
        padding = (int) ((DEFAULT_ALIGNMENT - (newOffset % DEFAULT_ALIGNMENT)) % DEFAULT_ALIGNMENT);
        mTotalPadding += padding;
      }

      final XEntry xentry = new XEntry(entry,
          outputEntryHeaderOffset, flags, padding);
      mXEntries.add(xentry);

      byte[] extra = entry.getExtra();
      if (extra == null) {
        extra = new byte[padding];
        Arrays.fill(extra, (byte) 0);
      } else {
        byte[] newExtra = new byte[extra.length + padding];
        System.arraycopy(extra, 0, newExtra, 0, extra.length);
        Arrays.fill(newExtra, extra.length, newExtra.length,
            (byte) 0);
        extra = newExtra;
      }
      entry.setExtra(extra);

      /*
       * Now write the header to output.
       */

      mOutputStream.writeInt(ZipOutputStream.LOCSIG);
      mOutputStream.writeShort(ZIP_ENTRY_VERSION);
      mOutputStream.writeShort(flags);
      mOutputStream.writeShort(entry.getMethod());

      final CalenderDateTime time = new CalenderDateTime(entry.getTime());

      mOutputStream.writeShort(time.getTime());
      mOutputStream.writeShort(time.getModDate());

      mOutputStream.writeInt(entry.getCrc());
      mOutputStream.writeInt(entry.getCompressedSize());
      mOutputStream.writeInt(entry.getSize());

      mOutputStream.writeShort(entry.getName().getBytes(StandardCharsets.UTF_8).length);
      mOutputStream.writeShort(entry.getExtra().length);
      mOutputStream.write(entry.getName().getBytes(StandardCharsets.UTF_8));
      mOutputStream.write(entry.getExtra(), 0, entry.getExtra().length);

      mInputFileOffset += inputEntryHeaderSize;

      final long sizeToCopy;
      if ((flags & ZIP_ENTRY_USES_DATA_DESCR) != 0)
        sizeToCopy = (entry.isDirectory() ? 0 : entry.getCompressedSize()) + ZIP_ENTRY_DATA_DESCRIPTOR_LEN;
      else
        sizeToCopy = entry.isDirectory() ? 0 : entry.getCompressedSize();

      if (sizeToCopy > 0) {
        mRafInput.seek(mInputFileOffset);

        long totalSizeCopied = 0;
        final byte[] buf = new byte[FILE_BUFFER];
        while (totalSizeCopied < sizeToCopy) {
          int read = mRafInput.read(buf, 0, (int) Math.min(FILE_BUFFER, sizeToCopy - totalSizeCopied));
          if (read <= 0)
            break;

          mOutputStream.write(buf, 0, read);
          totalSizeCopied += read;
        }
      }

      mInputFileOffset += sizeToCopy;
    }
  }

  private void buildCentralDirectory() throws IOException {
    final long centralDirOffset = mOutputStream.totalWritten();

    for (XEntry xentry : mXEntries) {
      final ZipEntry entry = xentry.entry();

      final CalenderDateTime time = new CalenderDateTime(entry.getTime());

      mOutputStream.writeInt(ZipFile.CENSIG);
      mOutputStream.writeShort(ZIP_ENTRY_VERSION);
      mOutputStream.writeShort(ZIP_ENTRY_VERSION);
      mOutputStream.writeShort(xentry.flags());
      mOutputStream.writeShort(entry.getMethod());
      mOutputStream.writeShort(time.getTime());
      mOutputStream.writeShort(time.getModDate());
      mOutputStream.writeInt(entry.getCrc());
      mOutputStream.writeInt(entry.getCompressedSize());
      mOutputStream.writeInt(entry.getSize());
      final byte[] nameBytes = entry.getName().getBytes(StandardCharsets.UTF_8);
      mOutputStream.writeShort(nameBytes.length);
      mOutputStream.writeShort(entry.getExtra() != null ? entry.getExtra().length - xentry.padding() : 0);
      final byte[] commentBytes;
      if (entry.getComment() != null) {
        commentBytes = entry.getComment().getBytes(StandardCharsets.UTF_8);
        mOutputStream.writeShort(Math.min(commentBytes.length, 0xffff));
      } else {
        commentBytes = null;
        mOutputStream.writeShort(0);
      }
      mOutputStream.writeShort(0);
      mOutputStream.writeShort(0);
      mOutputStream.writeInt(0);
      mOutputStream.writeInt(xentry.headerOffset());
      mOutputStream.write(nameBytes);
      if (entry.getExtra() != null)
        mOutputStream.write(entry.getExtra(), 0, entry.getExtra().length - xentry.padding());
      if (commentBytes != null)
        mOutputStream.write(commentBytes, 0, Math.min(commentBytes.length, 0xffff));
    }

    final long centralDirSize = mOutputStream.totalWritten() - centralDirOffset;

    final int entryCount = mXEntries.size();

    mOutputStream.writeInt(ZipFile.ENDSIG);
    mOutputStream.writeShort(0);
    mOutputStream.writeShort(0);
    mOutputStream.writeShort(entryCount);
    mOutputStream.writeShort(entryCount);
    mOutputStream.writeInt(centralDirSize);
    mOutputStream.writeInt(centralDirOffset);
    if (mZipFile.getComment() != null) {
      final byte[] bytes = mZipFile.getComment().getBytes(StandardCharsets.UTF_8);
      mOutputStream.writeShort(bytes.length);
      mOutputStream.write(bytes);
    } else {
      mOutputStream.writeShort(0);
    }

    mOutputStream.flush();
  }

  private static class CalenderDateTime {
    private final int modDate;
    private final int time;

    public CalenderDateTime(final long entryTime) {
      int modDate;
      int time;
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(new Date(entryTime));
      int year = cal.get(Calendar.YEAR);
      if (year < 1980) {
        modDate = 0x21;
        time = 0;
      } else {
        modDate = cal.get(Calendar.DATE);
        modDate = (cal.get(Calendar.MONTH) + 1 << 5) | modDate;
        modDate = ((cal.get(Calendar.YEAR) - 1980) << 9) | modDate;
        time = cal.get(Calendar.SECOND) >> 1;
        time = (cal.get(Calendar.MINUTE) << 5) | time;
        time = (cal.get(Calendar.HOUR_OF_DAY) << 11) | time;
      }

      this.modDate = modDate;
      this.time = time;
    }

    public int getModDate() {
      return modDate;
    }

    public int getTime() {
      return time;
    }
  }
}

