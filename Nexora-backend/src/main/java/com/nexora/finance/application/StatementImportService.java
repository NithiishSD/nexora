package com.nexora.finance.application;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.nexora.finance.api.dto.StatementImportResponse;
import com.nexora.finance.api.dto.StatementImportResponse.StatementRowMatch;
import com.nexora.finance.domain.AccountType;
import com.nexora.finance.domain.Direction;
import com.nexora.finance.domain.FinancialAccount;
import com.nexora.finance.domain.FinancialTransaction;
import com.nexora.finance.infrastructure.FinancialAccountRepository;
import com.nexora.finance.infrastructure.FinancialTransactionRepository;
import com.nexora.shared.domain.Money;
import com.nexora.shared.error.BusinessException;
import com.nexora.shared.error.ErrorCode;
import com.nexora.shared.error.ResourceNotFoundException;

@Service
public class StatementImportService {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private final FinancialAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final Clock clock;

    public StatementImportService(FinancialAccountRepository accounts, FinancialTransactionRepository transactions, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatementImportResponse importStatement(UUID accountId, MultipartFile file) {
        FinancialAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getAccountType() != AccountType.BANK) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Statement import is only supported for bank accounts.");
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Statement file is empty.");
        }

        List<ParsedRow> parsedRows = parseCsv(file);
        if (parsedRows.isEmpty()) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Statement file contains no valid transaction rows.");
        }

        // Query candidate transactions
        LocalDate minDate = parsedRows.stream().map(ParsedRow::date).min(LocalDate::compareTo).orElse(LocalDate.now(clock)).minusDays(4);
        LocalDate maxDate = parsedRows.stream().map(ParsedRow::date).max(LocalDate::compareTo).orElse(LocalDate.now(clock)).plusDays(4);

        List<FinancialTransaction> candidateTxs = transactions.findAll((root, query, cb) -> cb.and(
                cb.equal(root.get("accountId"), accountId),
                cb.greaterThanOrEqualTo(root.get("transactionDate"), minDate),
                cb.lessThanOrEqualTo(root.get("transactionDate"), maxDate)
        ));

        Set<UUID> matchedTxIds = new HashSet<>();
        List<StatementRowMatch> items = new ArrayList<>();
        int suggestedMatches = 0;

        for (ParsedRow row : parsedRows) {
            UUID matchedId = null;
            for (FinancialTransaction tx : candidateTxs) {
                if (!matchedTxIds.contains(tx.getId())
                        && tx.getDirection() == row.direction()
                        && tx.getAmount().compareTo(row.amount()) == 0
                        && Math.abs(java.time.temporal.ChronoUnit.DAYS.between(tx.getTransactionDate(), row.date())) <= 3) {
                    matchedId = tx.getId();
                    matchedTxIds.add(matchedId);
                    suggestedMatches++;
                    break;
                }
            }
            items.add(new StatementRowMatch(row.date(), row.description(), row.amount(), row.direction(), matchedId));
        }

        return new StatementImportResponse(UUID.randomUUID(), accountId, parsedRows.size(), suggestedMatches, items);
    }

    private List<ParsedRow> parseCsv(MultipartFile file) {
        List<ParsedRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("date") && (lower.contains("amount") || lower.contains("withdrawal") || lower.contains("deposit"))) {
                    continue; // Skip header
                }

                List<String> cols = splitCsv(line);
                if (cols.size() < 2) {
                    continue;
                }

                ParsedRow row = parseRow(cols);
                if (row != null) {
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MALFORMED_REQUEST, "Could not parse statement file: " + e.getMessage());
        }
        return rows;
    }

    private ParsedRow parseRow(List<String> cols) {
        LocalDate date = parseDate(cols.get(0));
        if (date == null) {
            return null;
        }

        // Patterns:
        // 1. Date, Description, Amount, Direction
        // 2. Date, Description, Withdrawal, Deposit
        // 3. Date, Description, Amount
        if (cols.size() >= 4) {
            Direction dir = parseDirection(cols.get(3));
            if (dir != null) {
                BigDecimal amt = parseAmount(cols.get(2));
                if (amt != null) {
                    return new ParsedRow(date, cols.get(1), amt.abs(), dir);
                }
            }

            BigDecimal withdrawal = parseAmount(cols.get(2));
            BigDecimal deposit = parseAmount(cols.get(3));
            if (withdrawal != null && withdrawal.compareTo(BigDecimal.ZERO) > 0) {
                return new ParsedRow(date, cols.get(1), withdrawal, Direction.OUT);
            }
            if (deposit != null && deposit.compareTo(BigDecimal.ZERO) > 0) {
                return new ParsedRow(date, cols.get(1), deposit, Direction.IN);
            }
        }

        if (cols.size() >= 3) {
            BigDecimal amt = parseAmount(cols.get(2));
            if (amt != null) {
                Direction dir = amt.compareTo(BigDecimal.ZERO) >= 0 ? Direction.IN : Direction.OUT;
                return new ParsedRow(date, cols.get(1), amt.abs(), dir);
            }
        }

        if (cols.size() == 2) {
            BigDecimal amt = parseAmount(cols.get(1));
            if (amt != null) {
                Direction dir = amt.compareTo(BigDecimal.ZERO) >= 0 ? Direction.IN : Direction.OUT;
                return new ParsedRow(date, "", amt.abs(), dir);
            }
        }

        return null;
    }

    private LocalDate parseDate(String raw) {
        String clean = raw.trim().replace("\"", "");
        for (DateTimeFormatter fmt : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String clean = raw.trim().replace("\"", "").replace(",", "").replace("₹", "").replace("$", "");
            if (clean.isEmpty() || clean.equals("-")) {
                return null;
            }
            return Money.round(new BigDecimal(clean));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Direction parseDirection(String raw) {
        if (raw == null) {
            return null;
        }
        String clean = raw.trim().replace("\"", "").toUpperCase(Locale.ROOT);
        if (clean.equals("IN") || clean.equals("CR") || clean.equals("CREDIT") || clean.equals("DEPOSIT")) {
            return Direction.IN;
        }
        if (clean.equals("OUT") || clean.equals("DR") || clean.equals("DEBIT") || clean.equals("WITHDRAWAL")) {
            return Direction.OUT;
        }
        return null;
    }

    private List<String> splitCsv(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if ((c == ',' || c == ';') && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private record ParsedRow(LocalDate date, String description, BigDecimal amount, Direction direction) {
    }
}
