package tablepatch;

import java.util.ArrayList;
import java.util.List;

/** Strict comma-separated records with quoted multiline fields and UTF-8 BOM support. */
public final class Csv {
    private Csv() {}

    public static List<List<String>> parse(String input) {
        if (input.startsWith("\uFEFF")) input = input.substring(1);
        if (input.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL characters are not supported");
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false, started = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(c);
                continue;
            }
            if (c == ',' || c == '\r' || c == '\n') {
                row.add(field.toString()); field.setLength(0); closed = false; started = false;
                if (row.size() > 1024) throw new IllegalArgumentException("too many fields in a record");
                if (c != ',') {
                    records.add(List.copyOf(row)); row.clear();
                    if (records.size() > 200_010) throw new IllegalArgumentException("too many records");
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                } else started = true;
            } else if (c == '"' && field.isEmpty() && !closed) {
                quoted = true; started = true;
            } else {
                if (closed || c == '"') throw new IllegalArgumentException("unexpected character outside a quoted field");
                field.append(c); started = true;
            }
        }
        if (quoted) throw new IllegalArgumentException("unterminated quoted field");
        if (started || closed || !row.isEmpty()) { row.add(field.toString()); records.add(List.copyOf(row)); }
        return records;
    }

    public static String format(List<List<String>> records) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : records) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) out.append(',');
                String value = row.get(i);
                if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    out.append('"').append(value.replace("\"", "\"\"")).append('"');
                } else out.append(value);
            }
            out.append('\n');
        }
        return out.toString();
    }
}
