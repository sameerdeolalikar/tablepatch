package tablepatch;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Main {
    private Main() {}

    static String read(Path path, long limit) throws IOException {
        if (Files.size(path) > limit) throw new IOException("input exceeds " + (limit / 1_000_000) + " MB: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** Publish a completed sibling file using a no-clobber hard link. */
    public static void writeNew(Path output, String text) throws IOException {
        Path absolute = output.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".tablepatch-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.createLink(absolute, temp);
        } finally { Files.deleteIfExists(temp); }
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || args[0].equals("--help")) {
            out.println("TablePatch\nserve [PORT]\ncompare BEFORE.csv AFTER.csv --key COLUMN --output changes.csv\napply BEFORE.csv PATCH.csv --output updated.csv\nOutputs must not already exist. Local filesystem hard-link support is required.");
            return 0;
        }
        try {
            if (args.length < 5 || (!args[0].equals("compare") && !args[0].equals("apply"))) throw new IllegalArgumentException("invalid command; use --help");
            Map<String, String> options = new HashMap<>();
            for (int i = 3; i < args.length; i += 2) {
                if (i + 1 >= args.length || !java.util.Set.of("--key", "--output").contains(args[i])
                        || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IllegalArgumentException("invalid or repeated option");
            }
            if (!options.containsKey("--output")) throw new IllegalArgumentException("--output is required");
            String beforeText = read(Path.of(args[1]), 20_000_000);
            String second = read(Path.of(args[2]), args[0].equals("apply") ? 64_000_000 : 20_000_000);
            String result;
            if (args[0].equals("compare")) {
                if (!options.containsKey("--key")) throw new IllegalArgumentException("--key is required");
                String key = options.get("--key");
                result = Patch.compare(Table.parse(beforeText, key), Table.parse(second, key));
            } else {
                if (options.containsKey("--key")) throw new IllegalArgumentException("apply reads the key from the patch");
                result = Patch.apply(Table.parse(beforeText, Patch.key(second)), second).csv();
            }
            writeNew(Path.of(options.get("--output")), result);
            out.println("Created " + options.get("--output")); return 0;
        } catch (IOException | IllegalArgumentException | UnsupportedOperationException exception) {
            err.println("tablepatch: " + exception.getMessage()); return 2;
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("serve")) {
            try { Web.start(args.length == 2 ? Integer.parseInt(args[1]) : 7411); }
            catch (IOException | IllegalArgumentException error) { System.err.println(error.getMessage()); System.exit(2); }
            return;
        }
        System.exit(run(args, System.out, System.err));
    }
}
