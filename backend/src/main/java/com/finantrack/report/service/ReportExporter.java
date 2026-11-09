package com.finantrack.report.service;

import com.finantrack.report.dto.ReportResponse;
import com.finantrack.report.dto.ReportResponse.Column;
import com.finantrack.report.dto.ReportResponse.ColumnKind;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/** Converte um relatório já calculado em arquivo: nenhum valor é recalculado aqui. */
@Component
public class ReportExporter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String SEPARATOR = ";";
    private static final String FORMULA_PREFIXES = "=+-@\t\r";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale BRAZIL = Locale.of("pt", "BR");
    private static final Color HEADER_BACKGROUND = new Color(0xEE, 0xEA, 0xFB);

    /** Com ";" e vírgula decimal, e BOM para o Excel em português reconhecer os acentos. */
    public byte[] toCsv(ReportResponse report) {
        StringBuilder csv = new StringBuilder();
        csv.append(report.columns().stream().map(column -> csvText(column.label())).collect(Collectors.joining(SEPARATOR)))
                .append("\r\n");
        report.rows().forEach(row -> csv.append(csvLine(report.columns(), row)));
        csv.append(csvLine(report.columns(), report.totals()));

        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] file = new byte[UTF8_BOM.length + content.length];
        System.arraycopy(UTF8_BOM, 0, file, 0, UTF8_BOM.length);
        System.arraycopy(content, 0, file, UTF8_BOM.length, content.length);
        return file;
    }

    /** A moeda escolhida muda só o símbolo: os valores não são convertidos. */
    public byte[] toPdf(ReportResponse report, String currencyCode) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (Document document = new Document(PageSize.A4, 36, 36, 36, 36)) {
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph(report.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));

            Font regular = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            document.add(new Paragraph("Período: %s a %s".formatted(report.from().format(DATE), report.to().format(DATE)), regular));
            String indicators = report.indicators().stream()
                    .map(item -> item.label() + ": " + currency(item.value(), currencyCode))
                    .collect(Collectors.joining("   |   "));
            Paragraph summary = new Paragraph(indicators, bold);
            summary.setSpacingAfter(12);
            document.add(summary);

            document.add(table(report, currencyCode, regular, bold));
        }
        return output.toByteArray();
    }

    private PdfPTable table(ReportResponse report, String currencyCode, Font regular, Font bold) {
        List<Column> columns = report.columns();
        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        columns.forEach(column -> {
            PdfPCell cell = cell(column.label(), column.kind(), bold);
            cell.setBackgroundColor(HEADER_BACKGROUND);
            table.addCell(cell);
        });
        report.rows().forEach(row -> addRow(table, columns, row, currencyCode, regular));
        addRow(table, columns, report.totals(), currencyCode, bold);
        return table;
    }

    private void addRow(PdfPTable table, List<Column> columns, List<String> row, String currencyCode, Font font) {
        IntStream.range(0, columns.size()).forEach(index -> {
            ColumnKind kind = columns.get(index).kind();
            table.addCell(cell(pdfValue(kind, row.get(index), currencyCode), kind, font));
        });
    }

    private PdfPCell cell(String text, ColumnKind kind, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        boolean numeric = kind == ColumnKind.MONEY || kind == ColumnKind.PERCENT || kind == ColumnKind.INTEGER;
        cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return cell;
    }

    private String csvLine(List<Column> columns, List<String> row) {
        return IntStream.range(0, columns.size())
                .mapToObj(index -> csvValue(columns.get(index).kind(), row.get(index)))
                .collect(Collectors.joining(SEPARATOR)) + "\r\n";
    }

    private String csvValue(ColumnKind kind, String value) {
        return switch (kind) {
            case MONEY, PERCENT -> value.replace('.', ',');
            case DATE -> csvText(date(value));
            case TEXT -> csvText(value);
            case INTEGER -> value;
        };
    }

    /** O prefixo impede que uma descrição como "=SOMA(...)" seja executada como fórmula na planilha. */
    private String csvText(String value) {
        String safe = !value.isEmpty() && FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(SEPARATOR) || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                ? "\"" + safe.replace("\"", "\"\"") + "\""
                : safe;
    }

    private String pdfValue(ColumnKind kind, String value, String currencyCode) {
        if (value.isEmpty()) {
            return value;
        }
        return switch (kind) {
            case MONEY -> currency(new BigDecimal(value), currencyCode);
            case PERCENT -> value.replace('.', ',') + "%";
            case DATE -> date(value);
            case TEXT, INTEGER -> value;
        };
    }

    /** A linha de total traz "Total" na primeira coluna, mesmo quando ela é de datas. */
    private String date(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(value).format(DATE) : value;
    }

    private String currency(BigDecimal value, String currencyCode) {
        NumberFormat format = NumberFormat.getCurrencyInstance(BRAZIL);
        format.setCurrency(Currency.getInstance(currencyCode));
        return format.format(value).replace(' ', ' ');
    }
}
