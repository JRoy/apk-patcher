package io.github.jroy.apkpatcher;

import io.github.jroy.apkpatcher.util.Logger;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.helper.HelpScreenException;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class ApkPatcherCli {
  public static void main(String[] args) {
    Logger.info("Loading ApkPatcher...");

    ArgumentParser parser = ArgumentParsers.newFor("ApkPatcher").build().defaultHelp(true);
    parser.addArgument("--search-only").help("If present, ApkPatcher will only search through the decoded apk and not patch them.").action(Arguments.storeTrue());
    parser.addArgument("--no-decode").help("If present, ApkPatcher will not decode an apk. It will just use the output directory.").action(Arguments.storeTrue());
    parser.addArgument("--compile-only").help("If present ApkPatcher will only build the apk from the output directory without decoding/patching the output directory.").action(Arguments.storeTrue());
    parser.addArgument("--input-apk").help("Sets the input apk file name.").setDefault("input.apk").action(Arguments.store());
    parser.addArgument("--output-apk").help("Sets the output apk file name.").setDefault("patched.apk").action(Arguments.store());
    parser.addArgument("--keystore-file").help("Sets the keystore file name.").setDefault("default.keystore").action(Arguments.store());
    parser.addArgument("--keystore-pass").help("Sets the keystore password.").setDefault("password").action(Arguments.store());
    parser.addArgument("--key-pass").help("Sets the key password.").setDefault("password").action(Arguments.store());

    Namespace namespace;
    try {
      namespace = parser.parseArgs(args);
      if (namespace == null) {
        Logger.error("Namespace is null!");
        System.exit(1);
        return;
      }
    } catch (HelpScreenException e) {
      System.exit(0);
    } catch (ArgumentParserException e) {
      e.printStackTrace();
      System.exit(0);
    }

    //TODO

    Logger.info("All Done :)");
  }

}
