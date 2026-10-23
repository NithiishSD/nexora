package com.nexora.reporting.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

import com.nexora.shared.error.FieldValidationException;

// Turns any API response (as its JSON tree, so dates and numbers look exactly as in JSON) into CSV (FR-RPT-16):
// - a page: its content rows; an array: its elements; an object with one array: that array's rows;
// - an object with several arrays (or none): a "section" column, a summary row of its other fields, then each
//   array's rows. Nested objects become dotted columns (customer.name); nested arrays become one row per element
//   with the parent's fields repeated. Money keeps its 2 decimals and weight its 3 (the JSON scale).
public final class CsvExporter {

    public static final int MAX_ROWS = 50_000;

    private CsvExporter() {
    }

    public static String toCsv(JsonNode root) {
        List<Map<String, String>> rows = rows(root);
        if (rows.size() > MAX_ROWS) {
            throw new FieldValidationException("from", "More than " + MAX_ROWS + " rows match; narrow the period.");
        }
        Set<String> headers = new LinkedHashSet<>();
        rows.forEach(r -> headers.addAll(r.keySet()));
        StringBuilder csv = new StringBuilder();
        line(csv, new ArrayList<>(headers));
        for (Map<String, String> row : rows) {
            line(csv, headers.stream().map(h -> row.getOrDefault(h, "")).toList());
        }
        return csv.toString();
    }

    private static List<Map<String, String>> rows(JsonNode root) {
        if (root.isArray()) {
            return elements(root, "");
        }
        if (root.has("content") && root.has("totalElements") && root.get("content").isArray()) {
            return elements(root.get("content"), "");
        }
        List<String> arrays = new ArrayList<>();
        root.propertyNames().forEach(name -> {
            if (root.get(name).isArray()) {
                arrays.add(name);
            }
        });
        if (arrays.size() == 1) {
            return elements(root.get(arrays.getFirst()), "");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        Map<String, String> summary = new LinkedHashMap<>();
        root.propertyNames().forEach(name -> {
            if (!arrays.contains(name)) {
                flattenInto(summary, root.get(name), name);
            }
        });
        if (!summary.isEmpty()) {
            rows.add(withSection("summary", summary));
        }
        for (String name : arrays) {
            elements(root.get(name), "").forEach(r -> rows.add(withSection(name, r)));
        }
        return rows;
    }

    private static List<Map<String, String>> elements(JsonNode array, String prefix) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (JsonNode element : array) {
            if (element.isObject()) {
                rows.addAll(objectRows(element, prefix));
            } else {
                Map<String, String> row = new LinkedHashMap<>();
                row.put(prefix.isEmpty() ? "value" : prefix.substring(0, prefix.length() - 1), scalar(element));
                rows.add(row);
            }
        }
        return rows;
    }

    // One object -> one row, or one row per element of each nested array (parent fields repeated).
    private static List<Map<String, String>> objectRows(JsonNode object, String prefix) {
        Map<String, String> base = new LinkedHashMap<>();
        List<List<Map<String, String>>> nested = new ArrayList<>();
        object.propertyNames().forEach(name -> {
            JsonNode value = object.get(name);
            if (value.isArray()) {
                nested.add(elements(value, prefix + name + "."));
            } else if (value.isObject()) {
                List<Map<String, String>> sub = objectRows(value, prefix + name + ".");
                if (sub.size() == 1) {
                    base.putAll(sub.getFirst());
                } else {
                    nested.add(sub);
                }
            } else {
                base.put(prefix + name, scalar(value));
            }
        });
        List<Map<String, String>> rows = new ArrayList<>();
        for (List<Map<String, String>> children : nested) {
            for (Map<String, String> child : children) {
                Map<String, String> row = new LinkedHashMap<>(base);
                row.putAll(child);
                rows.add(row);
            }
        }
        return rows.isEmpty() ? List.of(base) : rows;
    }

    private static void flattenInto(Map<String, String> row, JsonNode value, String key) {
        if (value.isObject()) {
            value.propertyNames().forEach(name -> flattenInto(row, value.get(name), key + "." + name));
        } else {
            row.put(key, scalar(value));
        }
    }

    private static Map<String, String> withSection(String section, Map<String, String> row) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("section", section);
        result.putAll(row);
        return result;
    }

    private static String scalar(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        if (value.isNumber()) {
            return value.isIntegralNumber() ? value.asString() : value.decimalValue().toPlainString();
        }
        String text = value.asString();
        // a text cell starting like a formula would run in a spreadsheet: keep it text (CSV injection)
        return !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
    }

    // RFC 4180: quote a cell holding a comma, quote or line break; double the quotes inside.
    private static void line(StringBuilder csv, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            if (i > 0) {
                csv.append(',');
            }
            if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                csv.append('"').append(cell.replace("\"", "\"\"")).append('"');
            } else {
                csv.append(cell);
            }
        }
        csv.append("\r\n");
    }
}
