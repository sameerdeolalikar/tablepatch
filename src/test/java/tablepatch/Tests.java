package tablepatch;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Tests {
    private static int passed;
    private static void check(boolean condition) { if (!condition) throw new AssertionError("condition failed"); }
    private static void rejects(Runnable operation) { try { operation.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("expected rejection"); }
    private static void test(String name, Runnable operation) { operation.run(); passed++; System.out.println("PASS " + name); }
    private static Table table(String csv) { return Table.parse(csv, "sku"); }

    public static void main(String[] args) throws Exception {
        test("quoted commas, newlines, and escaped quotes", () -> {
            var records = Csv.parse("sku,note\r\na,\"line 1\r\nline 2, \"\"quoted\"\"\"\r\n");
            check(records.get(1).get(1).equals("line 1\r\nline 2, \"quoted\""));
            check(Csv.parse(Csv.format(records)).equals(records));
        });
        test("BOM, trailing empty field, and no final newline", () -> check(Csv.parse("\uFEFFa,b\n1,").equals(List.of(List.of("a","b"), List.of("1","")))));
        test("malformed quoting is rejected", () -> {
            rejects(() -> Csv.parse("a,\"unterminated")); rejects(() -> Csv.parse("a,\"ok\"junk")); rejects(() -> Csv.parse("a,un\"quoted"));
        });
        test("key and schema validation", () -> {
            for (String input : List.of("sku,sku\na,b", "sku,n\na,b\na,c", "sku,n\n,b", "sku,n\na", "other\na")) rejects(() -> table(input));
        });
        test("add update remove and ordering round trip", () -> {
            var before = table("sku,name\na,Alpha\nb,Beta\nc,Gamma\n");
            var after = table("sku,name\nd,Delta\nb,Changed\na,Alpha\n");
            check(Patch.apply(before, Patch.compare(before, after)).csv().equals(after.csv()));
        });
        test("changed source rejects patch", () -> {
            var before = table("sku,n\na,1\n"); var after = table("sku,n\na,2\n");
            String patch = Patch.compare(before, after);
            rejects(() -> Patch.apply(table("sku,n\na,3\n"), patch));
        });
        test("altered patch fails target hash", () -> {
            var before = table("sku,n\na,1\n"); var after = table("sku,n\na,2\n");
            String patch = Patch.compare(before, after).replace("update,a,1,a,2", "update,a,1,a,9");
            rejects(() -> Patch.apply(before, patch));
        });
        test("zero rows and no changes", () -> {
            var empty = table("sku,n\n"); var filled = table("sku,n\na,1\n");
            check(Patch.apply(filled, Patch.compare(filled, empty)).csv().equals(empty.csv()));
            check(Patch.apply(empty, Patch.compare(empty, empty)).csv().equals(empty.csv()));
        });
        test("text values preserve leading zeros and decimal precision", () -> {
            var before = table("sku,n\n001,0.10000000000000000001\n");
            check(Patch.apply(before, Patch.compare(before, before)).csv().equals(before.csv()));
        });
        test("generated CSV round trips", () -> {
            String[] cells = {"", "x", "a,b", "line\nnext", "\"quote\"", "日本語", "  spaces  "};
            for (String a : cells) for (String b : cells) {
                var rows = List.of(List.of("first", "second"), List.of(a,b));
                check(Csv.parse(Csv.format(rows)).equals(rows));
            }
        });
        test("visual workspace limits do not restrict CLI tables", () -> {
            StringBuilder csv = new StringBuilder("sku,n\n");
            for (int i = 0; i < 2001; i++) csv.append(i).append(",value\n");
            check(table(csv.toString()).rows().size() == 2001);
            rejects(() -> Web.compare(java.util.Map.of("before", csv.toString(), "after", csv.toString(), "key", "sku")));
        });
        Path dir = Files.createTempDirectory("tablepatch-test-");
        try {
            Path before = dir.resolve("before.csv"), after = dir.resolve("after.csv"), patch = dir.resolve("patch.csv"), output = dir.resolve("output.csv");
            Files.writeString(before, "sku,n\na,1\n"); Files.writeString(after, "sku,n\na,2\n");
            var sink = new PrintStream(new ByteArrayOutputStream());
            test("CLI compare and apply", () -> {
                check(Main.run(new String[]{"compare",before.toString(),after.toString(),"--key","sku","--output",patch.toString()},sink,sink)==0);
                check(Main.run(new String[]{"apply",before.toString(),patch.toString(),"--output",output.toString()},sink,sink)==0);
            });
            check(Files.readString(output).equals(Files.readString(after)));
            test("CLI cannot overwrite existing output", () -> check(Main.run(new String[]{"apply",before.toString(),patch.toString(),"--output",output.toString()},sink,sink)==2));
            check(Files.readString(output).equals(Files.readString(after)));
        } finally {
            try (var paths = Files.list(dir)) { for (Path file : paths.toList()) Files.delete(file); }
            Files.delete(dir);
        }
        System.out.println(passed + " tests passed.");
    }
}
