package tablepatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Table(List<String> columns, String key, Map<String, List<String>> rows) {
    public static Table parse(String csv, String key) {
        List<List<String>> records = Csv.parse(csv);
        if (records.isEmpty()) throw new IllegalArgumentException("CSV has no header");
        List<String> header = records.getFirst();
        if (header.size() > 256 || header.stream().anyMatch(String::isEmpty)
                || new HashSet<>(header).size() != header.size()) {
            throw new IllegalArgumentException("header must contain 1–256 unique, nonempty names");
        }
        int keyIndex = header.indexOf(key);
        if (keyIndex < 0) throw new IllegalArgumentException("key column not found: " + key);
        if (records.size() > 100_001) throw new IllegalArgumentException("table exceeds 100,000 rows");
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (List<String> row : records.subList(1, records.size())) {
            if (row.size() != header.size()) throw new IllegalArgumentException("ragged CSV record");
            String value = row.get(keyIndex);
            if (value.isEmpty()) throw new IllegalArgumentException("empty key");
            if (rows.putIfAbsent(value, List.copyOf(row)) != null) throw new IllegalArgumentException("duplicate key: " + value);
        }
        return new Table(List.copyOf(header), key, rows);
    }

    public String csv() {
        List<List<String>> records = new ArrayList<>();
        records.add(columns); records.addAll(rows.values());
        return Csv.format(records);
    }
}
