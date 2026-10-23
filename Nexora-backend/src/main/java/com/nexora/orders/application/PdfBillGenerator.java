package com.nexora.orders.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.nexora.orders.domain.CustomerOrder;
import com.nexora.orders.domain.OrderItem;
import com.nexora.parties.api.dto.PartyResponse;

/**
 * Standard PDF-1.4 document generator for customer sales bills.
 * Produces valid, standalone PDF byte streams conforming to ISO 32000-1 without external dependencies.
 */
public class PdfBillGenerator {

    public static byte[] generateSalesBill(CustomerOrder order, PartyResponse customer, LocalDate billDate) {
        try {
            ByteArrayOutputStream streamContent = new ByteArrayOutputStream();
            StringBuilder sb = new StringBuilder();

            sb.append("BT\n");
            // Title Header
            sb.append("/F1 18 Tf\n");
            sb.append("50 800 Td\n");
            sb.append("(").append(escapePdf("SMART SILK TRADERS")).append(") Tj\n");

            sb.append("/F2 10 Tf\n");
            sb.append("0 -16 Td\n");
            sb.append("(").append(escapePdf("Sales Bill / Tax Invoice")).append(") Tj\n");

            // Order & Bill Metadata
            sb.append("0 -24 Td\n");
            sb.append("/F1 11 Tf\n");
            sb.append("(").append(escapePdf("Bill Ref: BILL-" + order.getOrderNumber())).append(") Tj\n");
            sb.append("/F2 10 Tf\n");
            sb.append("300 0 Td\n");
            sb.append("(").append(escapePdf("Bill Date: " + billDate)).append(") Tj\n");

            sb.append("-300 -18 Td\n");
            sb.append("(").append(escapePdf("Order No: " + order.getOrderNumber())).append(") Tj\n");
            sb.append("300 0 Td\n");
            sb.append("(").append(escapePdf("Delivered On: " + (order.getDeliveredOn() != null ? order.getDeliveredOn() : billDate))).append(") Tj\n");

            // Customer Details
            sb.append("-300 -24 Td\n");
            sb.append("/F1 11 Tf\n");
            sb.append("(").append(escapePdf("Billed To:")).append(") Tj\n");
            sb.append("/F2 10 Tf\n");
            sb.append("0 -15 Td\n");
            sb.append("(").append(escapePdf(customer.name() + " (" + customer.partyCode() + ")")).append(") Tj\n");

            String address = formatAddress(customer);
            if (!address.isEmpty()) {
                sb.append("0 -14 Td\n");
                sb.append("(").append(escapePdf(address)).append(") Tj\n");
            }

            // Table Header
            sb.append("0 -28 Td\n");
            sb.append("/F1 10 Tf\n");
            sb.append("(").append(escapePdf(String.format("%-4s  %-12s  %-20s  %12s  %12s  %14s",
                    "Line", "Product", "Specification", "Weight (kg)", "Rate (Rs)", "Amount (Rs)"))).append(") Tj\n");
            sb.append("0 -12 Td\n");
            sb.append("(").append(escapePdf("-------------------------------------------------------------------------------------------------------")).append(") Tj\n");

            // Table Rows
            sb.append("/F2 10 Tf\n");
            for (OrderItem item : order.getItems()) {
                String spec = item.getWarpSpecification() != null && !item.getWarpSpecification().isBlank()
                        ? item.getWarpSpecification().strip() : "-";
                if (spec.length() > 20) {
                    spec = spec.substring(0, 17) + "...";
                }
                String row = String.format("%-4d  %-12s  %-20s  %12.3f  %12.2f  %14.2f",
                        item.getLineNo(),
                        item.getProductType().name(),
                        spec,
                        item.getRequiredWeightKg(),
                        item.getRatePerKg(),
                        item.getAmount());
                sb.append("0 -16 Td\n");
                sb.append("(").append(escapePdf(row)).append(") Tj\n");
            }

            // Table Footer / Total
            sb.append("0 -14 Td\n");
            sb.append("(").append(escapePdf("-------------------------------------------------------------------------------------------------------")).append(") Tj\n");
            sb.append("/F1 11 Tf\n");
            sb.append("0 -18 Td\n");
            String totalRow = String.format("%-40s  %38s %14.2f", "Total Payable Amount:", "Rs.", order.totalAmount());
            sb.append("(").append(escapePdf(totalRow)).append(") Tj\n");

            // Disclaimer / Sign-off
            sb.append("/F2 9 Tf\n");
            sb.append("0 -40 Td\n");
            sb.append("(").append(escapePdf("Thank you for your business! This is a computer-generated invoice.")).append(") Tj\n");

            sb.append("ET\n");

            streamContent.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
            byte[] streamBytes = streamContent.toByteArray();

            // Construct PDF Objects
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            List<Integer> offsets = new ArrayList<>();

            pdf.write("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes(StandardCharsets.US_ASCII));

            // 1 0 obj: Catalog
            offsets.add(pdf.size());
            pdf.write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // 2 0 obj: Pages
            offsets.add(pdf.size());
            pdf.write("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // 3 0 obj: Page
            offsets.add(pdf.size());
            pdf.write("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>\nendobj\n"
                    .getBytes(StandardCharsets.US_ASCII));

            // 4 0 obj: Contents stream
            offsets.add(pdf.size());
            pdf.write(("4 0 obj\n<< /Length " + streamBytes.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
            pdf.write(streamBytes);
            pdf.write("\nendstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));

            // 5 0 obj: Font Bold
            offsets.add(pdf.size());
            pdf.write("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n"
                    .getBytes(StandardCharsets.US_ASCII));

            // 6 0 obj: Font Regular
            offsets.add(pdf.size());
            pdf.write("6 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                    .getBytes(StandardCharsets.US_ASCII));

            // xref
            int startXref = pdf.size();
            pdf.write(("xref\n0 " + (offsets.size() + 1) + "\n").getBytes(StandardCharsets.US_ASCII));
            pdf.write("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
            for (int offset : offsets) {
                pdf.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
            }

            // trailer
            pdf.write(("trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\n").getBytes(StandardCharsets.US_ASCII));
            pdf.write("startxref\n".getBytes(StandardCharsets.US_ASCII));
            pdf.write((startXref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));

            return pdf.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate sales bill PDF", e);
        }
    }

    private static String escapePdf(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private static String formatAddress(PartyResponse p) {
        List<String> parts = new ArrayList<>();
        if (p.addressLine() != null && !p.addressLine().isBlank()) parts.add(p.addressLine().strip());
        if (p.city() != null && !p.city().isBlank()) parts.add(p.city().strip());
        if (p.district() != null && !p.district().isBlank()) parts.add(p.district().strip());
        if (p.state() != null && !p.state().isBlank()) parts.add(p.state().strip());
        if (p.pincode() != null && !p.pincode().isBlank()) parts.add(p.pincode().strip());
        return String.join(", ", parts);
    }
}
