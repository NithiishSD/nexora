package com.nexora.documents.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// FR-DOC-06: Heuristic parser and extractor for supplier bill text and PDF streams.
public class BillOcrExtractor {

    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(?i)(?:weight|quantity|qty|ordered)?[:\\s]*([0-9]+(?:\\.[0-9]{1,3})?)\\s*(?:kg|kgs)");
    private static final Pattern RATE_PATTERN = Pattern.compile("(?i)(?:rate|price|unit\\s*rate|@)[:\\s]*(?:rs\\.?|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)|(?:rs\\.?|₹)\\s*([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:/|per)\\s*kg|([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:/|per)\\s*kg");
    private static final Pattern TOTAL_PATTERN = Pattern.compile("(?i)(?:total|gross|amount|net amount|bill amount)[:\\s]*(?:rs\\.?|₹)?\\s*([0-9]+(?:\\.[0-9]{1,2})?)");
    private static final Pattern BILL_NUM_PATTERN = Pattern.compile("(?i)(?:bill\\s*(?:no|num|#)|inv(?:oice)?\\s*(?:no|num|#))[:\\s]*([A-Za-z0-9_-]+)");
    private static final Pattern SUPPLIER_PATTERN = Pattern.compile("(?i)(?:supplier|from|seller|vendor)[:\\s]*([A-Za-z0-9\\s,.-]{2,50}?)(?=[\\r\\n]|\\s*(?:weight|rate|bill|inv|total|gross|date)|$)");

    public static Map<String, Object> extract(byte[] bytes, String fileName) {
        Map<String, Object> suggested = new LinkedHashMap<>();
        if (bytes == null || bytes.length == 0) {
            return suggested;
        }

        String text = extractText(bytes, fileName);

        // Extract weight
        Matcher weightMatcher = WEIGHT_PATTERN.matcher(text);
        if (weightMatcher.find()) {
            try {
                BigDecimal weight = new BigDecimal(weightMatcher.group(1)).setScale(3, RoundingMode.HALF_UP);
                suggested.put("orderedWeightKg", weight);
            } catch (Exception ignored) {
            }
        }

        // Extract rate
        Matcher rateMatcher = RATE_PATTERN.matcher(text);
        while (rateMatcher.find()) {
            try {
                String match = rateMatcher.group(1);
                if (match == null) {
                    match = rateMatcher.group(2);
                }
                if (match == null) {
                    match = rateMatcher.group(3);
                }
                if (match != null) {
                    BigDecimal rate = new BigDecimal(match).setScale(2, RoundingMode.HALF_UP);
                    if (rate.compareTo(BigDecimal.ZERO) > 0) {
                        suggested.put("ratePerKg", rate);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Extract bill number
        Matcher billMatcher = BILL_NUM_PATTERN.matcher(text);
        if (billMatcher.find()) {
            suggested.put("billNumber", billMatcher.group(1).trim());
        }

        // Extract supplier name
        Matcher supplierMatcher = SUPPLIER_PATTERN.matcher(text);
        if (supplierMatcher.find()) {
            suggested.put("supplierName", supplierMatcher.group(1).trim());
        }

        // Extract total or calculate from weight * rate
        Matcher totalMatcher = TOTAL_PATTERN.matcher(text);
        if (totalMatcher.find()) {
            try {
                BigDecimal total = new BigDecimal(totalMatcher.group(1)).setScale(2, RoundingMode.HALF_UP);
                suggested.put("grossAmount", total);
            } catch (Exception ignored) {
            }
        } else if (suggested.containsKey("orderedWeightKg") && suggested.containsKey("ratePerKg")) {
            BigDecimal weight = (BigDecimal) suggested.get("orderedWeightKg");
            BigDecimal rate = (BigDecimal) suggested.get("ratePerKg");
            suggested.put("grossAmount", weight.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        }

        return suggested;
    }

    private static String extractText(byte[] bytes, String fileName) {
        StringBuilder sb = new StringBuilder();
        // If PDF, extract text enclosed in (...) Tj
        if (startsWithPdf(bytes)) {
            Pattern pdfText = Pattern.compile("\\(([^)]+)\\)\\s*Tj");
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher m = pdfText.matcher(raw);
            while (m.find()) {
                sb.append(m.group(1)).append("\n");
            }
        }
        // Also append general text representation
        sb.append("\n").append(new String(bytes, StandardCharsets.UTF_8));
        if (fileName != null) {
            sb.append(" ").append(fileName);
        }
        return sb.toString();
    }

    private static boolean startsWithPdf(byte[] b) {
        return b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F';
    }
}
