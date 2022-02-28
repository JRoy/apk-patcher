package io.github.jroy.apkpatcher;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.androlib.ApkDecoder;
import brut.androlib.err.InFileNotFoundException;
import brut.androlib.options.BuildOptions;
import brut.common.BrutException;
import brut.directory.DirectoryException;
import com.android.apksig.ApkSigner;
import com.android.apksigner.PasswordRetriever;
import com.android.apksigner.SignerParams;
import io.github.jroy.apkpatcher.patcher.IApply;
import io.github.jroy.apkpatcher.util.FileSearcher;
import io.github.jroy.apkpatcher.util.Logger;
import io.github.jroy.apkpatcher.util.zipalign.ZipAligner;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.logging.LogManager;

public class ApkPatcher {
  private final File inputApk;
  private final File outputApk;
  private final boolean skipDecode;
  private final boolean skipPatch;
  private final boolean skipBuild;
  private final String prioritySearch;
  private final File keystoreFile;
  private final String keystoreAlias;
  private final String keystorePassword;
  private final String keyPassword;
  private final IApply[] patches;

  public ApkPatcher(File inputApk, File outputApk, boolean skipDecode, boolean skipPatch, boolean skipBuild, String prioritySearch, File keystoreFile, String keystoreAlias, String keystorePassword, String keyPassword, IApply[] patches) {
    this.inputApk = inputApk;
    this.outputApk = outputApk;
    this.skipDecode = skipDecode;
    this.skipPatch = skipPatch;
    this.skipBuild = skipBuild;
    this.prioritySearch = prioritySearch;
    this.keystoreFile = keystoreFile;
    this.keystoreAlias = keystoreAlias;
    this.keystorePassword = keystorePassword;
    this.keyPassword = keyPassword;
    this.patches = patches;
  }

  public void patch() throws IOException, ApkPatcherException {
    final File outputDir = new File("output/");

    if (!skipDecode) {
      decode(outputDir);
    }

    Logger.info((skipPatch ? "Searching" : "Patching") + " APK...");

    final FileSearcher fileSearcher = new FileSearcher(outputDir, prioritySearch, !skipPatch, patches);
    fileSearcher.searchAndApply();

    Logger.info("Removing APK Certificates...");
    final File apktoolFile = new File(outputDir, "apktool.yml");
    if (!apktoolFile.exists()) {
      throw new ApkPatcherException("Could not find apktool.yml file!");
    }

    final List<String> lines = FileUtils.readLines(apktoolFile, StandardCharsets.UTF_8);
    boolean found = false;
    for (final String line : lines) {
      if (line.trim().startsWith("stamp-")) {
        lines.remove(line);

        FileUtils.writeLines(apktoolFile, lines);

        final File stampFile = new File(new File(outputDir, "unknown"), line.trim().split(":")[0]);
        if (!stampFile.exists()) {
          Logger.warn("Could not find certificate file '" + stampFile.getPath() + "' despite decoder listing!");
          break;
        }

        if (stampFile.delete()) {
          Logger.info("Removed APK Certificates!");
        } else {
          Logger.error("Could not remove APK Certificates!");
        }
        found = true;
        break;
      }
    }

    if (!found) {
      Logger.warn("Could not find any APK Certificates, this is likely fine...");
    }

    Logger.info((skipPatch ? "Searched" : "Patched") + " APK!");

    if (!skipBuild) {
      Logger.info("Building APK...");

      final Androlib androlib = new Androlib(new BuildOptions());
      LogManager.getLogManager().reset(); // apktool does some weird stuff to the logger so just get rid of all that

      final File tmpBuildApk = File.createTempFile("apkbuild", ".apk");
      tmpBuildApk.deleteOnExit();

      try {
        androlib.build(outputDir, tmpBuildApk);
      } catch (BrutException e) {
        throw new ApkPatcherException("Error while building APK", e);
      }

      Logger.info("Aligning APK...");
      final File tmpAlignApk = File.createTempFile("apkalign", ".apk");
      tmpAlignApk.deleteOnExit();
      new ZipAligner(tmpBuildApk, tmpAlignApk).run();

      Logger.info("Signing APK...");

      try {
        SignerParams signer = new SignerParams();

        signer.setKeystoreFile(keystoreFile.getPath());
        signer.setKeystorePasswordSpec("pass:" + keystorePassword);
        signer.setKeyPasswordSpec("pass:" + keyPassword);
        signer.setKeystoreKeyAlias(keystoreAlias);
        signer.setName("signer #1");

        final PasswordRetriever passwordRetriever = new PasswordRetriever();

        try {
          signer.loadPrivateKeyAndCerts(passwordRetriever);
        } catch (Exception e) {
          throw new ApkPatcherException("Failed to load signer", e);
        }

        final ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(signer.getKeystoreKeyAlias(), signer.getPrivateKey(), signer.getCerts()).build();
        passwordRetriever.close();

        final File tmpOutputApk = File.createTempFile("apksigner", ".apk");
        tmpOutputApk.deleteOnExit();

        new ApkSigner.Builder(Collections.singletonList(signerConfig))
            .setInputApk(tmpAlignApk)
            .setOutputApk(tmpOutputApk)
            .build()
            .sign();

        Files.move(tmpOutputApk.toPath(), outputApk.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception e) {
        throw new ApkPatcherException("Error while signing apk", e);
      }

      Logger.info("Signed APK!");
    }
  }

  public void decode(final File outputDir) throws ApkPatcherException, IOException {
    if (outputDir.exists()) {
      Logger.warn("Deleting pre-existing output directory...");
      FileUtils.deleteDirectory(outputDir);
    }

    Logger.info("Decoding APK...");
    final ApkDecoder decoder = new ApkDecoder();
    LogManager.getLogManager().reset(); // apktool does some weird stuff to the logger so just get rid of all that
    decoder.setOutDir(outputDir);
    decoder.setApkFile(inputApk);

    try {
      decoder.decode();
    } catch (InFileNotFoundException e) {
      throw new ApkPatcherException("The target APK wasn't found!", e);
    } catch (IOException e) {
      throw new ApkPatcherException("Unable to modify apk file! Permissions?", e);
    } catch (AndrolibException e) {
      throw new ApkPatcherException("Error with output!", e);
    } catch (DirectoryException e) {
      throw new ApkPatcherException("Unable to modify dex structure! Permissions?", e);
    } finally {
      decoder.close();
    }
    Logger.info("APK Decoded!");
  }
}
