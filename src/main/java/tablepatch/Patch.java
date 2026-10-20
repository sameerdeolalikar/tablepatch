package tablepatch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned readable CSV patches bound to canonical source and target hashes. */
public final class Patch {
    private Patch() {}

    public static String hash(String data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static List<String> record(String kind, List<String> values) {
        List<String> result = new ArrayList<>(); result.add(kind); result.addAll(values); return result;
    }

    public static String compare(Table before, Table after) {
        if (!before.columns().equals(after.columns()) || !before.key().equals(after.key())) {
            throw new IllegalArgumentException("schemas and key columns must match exactly");
        }
        List<List<String>> records = new ArrayList<>();
        records.add(List.of("tablepatch", "1", hash(before.csv()), hash(after.csv())));
        records.add(List.of("key", before.key())); records.add(record("columns", before.columns()));
        for (String key : after.rows().keySet()) records.add(List.of("order", key));
        for (var entry : before.rows().entrySet()) {
            List<String> replacement = after.rows().get(entry.getKey());
            if (replacement == null) records.add(record("remove", entry.getValue()));
            else if (!entry.getValue().equals(replacement)) {
                List<String> values = new ArrayList<>(entry.getValue()); values.addAll(replacement);
                records.add(record("update", values));
            }
        }
        for (var entry : after.rows().entrySet()) {
            if (!before.rows().containsKey(entry.getKey())) records.add(record("add", entry.getValue()));
        }
        return Csv.format(records);
    }

    public static String key(String patch) {
        var records = Csv.parse(patch);
        if (records.size() < 3 || records.get(1).size() != 2 || !records.get(1).getFirst().equals("key")) {
            throw new IllegalArgumentException("invalid patch key record");
        }
        return records.get(1).get(1);
    }

    public static Table apply(Table before, String patch) {
        List<List<String>> records = Csv.parse(patch);
        if (records.size() < 3 || records.getFirst().size() != 4
                || !records.getFirst().subList(0, 2).equals(List.of("tablepatch", "1"))) {
            throw new IllegalArgumentException("unsupported or malformed patch");
        }
        if (!records.getFirst().get(2).equals(hash(before.csv()))) throw new IllegalArgumentException("source conflict: table changed since comparison");
        if (!records.get(1).equals(List.of("key", before.key()))
                || !records.get(2).equals(record("columns", before.columns()))) throw new IllegalArgumentException("patch schema mismatch");
        Map<String, List<String>> result = new LinkedHashMap<>(before.rows());
        List<String> order = new ArrayList<>();
        Set<String> modified = new HashSet<>();
        int width = before.columns().size(), keyIndex = before.columns().indexOf(before.key());
        for (List<String> row : records.subList(3, records.size())) {
            String operation = row.getFirst();
            if (operation.equals("order")) {
                if (row.size() != 2) throw new IllegalArgumentException("invalid order record");
                order.add(row.get(1)); continue;
            }
            int expected = operation.equals("update") ? 2 * width + 1 : width + 1;
            if (row.size() != expected || !Set.of("add", "remove", "update").contains(operation)) throw new IllegalArgumentException("invalid operation record");
            List<String> old = List.copyOf(row.subList(1, width + 1));
            String id = old.get(keyIndex);
            if (id.isEmpty() || !modified.add(id)) throw new IllegalArgumentException("empty or repeated operation key");
            if (operation.equals("add")) {
                if (result.putIfAbsent(id, old) != null) throw new IllegalArgumentException("add conflict");
            } else {
                if (!old.equals(result.get(id))) throw new IllegalArgumentException("before-image mismatch");
                if (operation.equals("remove")) result.remove(id);
                else {
                    List<String> replacement = List.copyOf(row.subList(width + 1, row.size()));
                    if (!id.equals(replacement.get(keyIndex))) throw new IllegalArgumentException("update cannot change the key");
                    result.put(id, replacement);
                }
            }
        }
        if (order.size() != result.size() || !new HashSet<>(order).equals(result.keySet())) throw new IllegalArgumentException("target ordering is invalid");
        Map<String, List<String>> sorted = new LinkedHashMap<>();
        for (String id : order) sorted.put(id, result.get(id));
        Table after = new Table(before.columns(), before.key(), sorted);
        if (!records.getFirst().get(3).equals(hash(after.csv()))) throw new IllegalArgumentException("target hash mismatch: patch is incomplete or altered");
        return after;
    }
}
