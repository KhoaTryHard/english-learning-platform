import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Generates only this service's key pair; refuses to replace existing files. */
class GenerateKey {
  public static void main(String[] args) throws Exception {
    Path directory = Path.of(args.length == 0 ? ".secrets" : args[0]);
    Path privateFile = directory.resolve("assessment.private.pem");
    Path publicFile = directory.resolve("assessment.public.pem");
    if (Files.exists(privateFile) || Files.exists(publicFile)) {
      throw new IllegalStateException("Key already exists; choose a new directory for rotation");
    }
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(3072);
    var pair = generator.generateKeyPair();
    Files.createDirectories(directory);
    Files.writeString(privateFile, pem("PRIVATE", pair.getPrivate().getEncoded()));
    Files.writeString(publicFile, pem("PUBLIC", pair.getPublic().getEncoded()));
    System.out.println("Created Assessment key pair. Share assessment.public.pem only.");
  }

  private static String pem(String type, byte[] bytes) {
    return "-----BEGIN "
        + type
        + " KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
        + "\n-----END "
        + type
        + " KEY-----\n";
  }
}
