package com.mindmap.agent.service;

import com.mindmap.agent.dto.AgentArtifact;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AgentArtifactService {

    private final Path artifactDir;

    public AgentArtifactService(
            @Value("${app.agent.artifact-path:${user.dir}/data/artifacts}") String artifactPath
    ) {
        this.artifactDir = Path.of(artifactPath);
    }

    public AgentArtifact create(String requestedFormat, String requestedTitle, String content) {
        String format = normalizeFormat(requestedFormat);
        String title = safeTitle(requestedTitle);
        String id = UUID.randomUUID().toString();
        String filename = title + "-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "." + format;
        Path path = artifactDir.resolve(id + "-" + filename).normalize();
        try {
            Files.createDirectories(artifactDir);
            byte[] bytes = switch (format) {
                case "docx" -> docxBytes(title, content);
                case "xlsx" -> xlsxBytes(title, content);
                case "pdf" -> pdfBytes(title, content);
                case "csv" -> content.getBytes(StandardCharsets.UTF_8);
                default -> content.getBytes(StandardCharsets.UTF_8);
            };
            Files.write(path, bytes);
            return new AgentArtifact(id, filename, format, "/api/agent/artifacts/" + id, bytes.length);
        } catch (IOException ex) {
            throw new IllegalArgumentException("生成文件失败：" + ex.getMessage(), ex);
        }
    }

    public Resource load(String id) {
        try {
            Files.createDirectories(artifactDir);
            Path path = Files.list(artifactDir)
                    .filter(item -> item.getFileName().toString().startsWith(id + "-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("文件不存在：" + id));
            return new UrlResource(path.toUri());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("文件路径不可访问：" + id, ex);
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取文件失败：" + ex.getMessage(), ex);
        }
    }

    public String filename(String id) {
        try {
            Files.createDirectories(artifactDir);
            return Files.list(artifactDir)
                    .filter(item -> item.getFileName().toString().startsWith(id + "-"))
                    .findFirst()
                    .map(item -> item.getFileName().toString().substring(id.length() + 1))
                    .orElse(id);
        } catch (IOException ex) {
            return id;
        }
    }

    private String normalizeFormat(String requestedFormat) {
        String format = requestedFormat == null ? "" : requestedFormat.trim().toLowerCase();
        return switch (format) {
            case "word", "doc", "docx" -> "docx";
            case "excel", "xls", "xlsx" -> "xlsx";
            case "pdf" -> "pdf";
            case "csv" -> "csv";
            case "md", "markdown" -> "md";
            default -> "txt";
        };
    }

    private String safeTitle(String requestedTitle) {
        String title = requestedTitle == null || requestedTitle.isBlank() ? "agent-output" : requestedTitle.trim();
        title = title.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        if (title.length() > 40) {
            title = title.substring(0, 40);
        }
        return title.isBlank() ? "agent-output" : title;
    }

    private byte[] docxBytes(String title, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zip, "word/document.xml", wordDocumentXml(title, content));
        }
        return out.toByteArray();
    }

    private String wordDocumentXml(String title, String content) {
        StringBuilder body = new StringBuilder();
        body.append(paragraph(title));
        for (String line : content.split("\\R")) {
            body.append(paragraph(line));
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>%s<w:sectPr/></w:body>
                </w:document>
                """.formatted(body);
    }

    private String paragraph(String text) {
        return "<w:p><w:r><w:t>" + xml(text) + "</w:t></w:r></w:p>";
    }

    private byte[] xlsxBytes(String title, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
            put(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(title, content));
        }
        return out.toByteArray();
    }

    private String sheetXml(String title, String content) {
        StringBuilder rows = new StringBuilder();
        int row = 1;
        rows.append(rowXml(row++, title));
        for (String line : content.split("\\R")) {
            rows.append(rowXml(row++, line));
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>%s</sheetData></worksheet>
                """.formatted(rows);
    }

    private String rowXml(int row, String value) {
        String[] cells = (value == null ? "" : value).split("\\t", -1);
        StringBuilder rowXml = new StringBuilder("<row r=\"" + row + "\">");
        for (int index = 0; index < cells.length; index++) {
            rowXml.append("<c r=\"")
                    .append(columnName(index))
                    .append(row)
                    .append("\" t=\"inlineStr\"><is><t>")
                    .append(xml(cells[index]))
                    .append("</t></is></c>");
        }
        rowXml.append("</row>");
        return rowXml.toString();
    }

    private String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    private byte[] pdfBytes(String title, String content) throws IOException {
        String text = (title + "\n\n" + content)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
        String stream = "BT /F1 12 Tf 50 780 Td 14 TL (" + text.replace("\n", ") Tj T* (") + ") Tj ET";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ListWithOffsets pdf = new ListWithOffsets(out);
        pdf.write("%PDF-1.4\n");
        pdf.obj("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.obj("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        pdf.obj("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");
        pdf.obj("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        pdf.obj("5 0 obj\n<< /Length " + stream.getBytes(StandardCharsets.UTF_8).length + " >>\nstream\n" + stream + "\nendstream\nendobj\n");
        int xref = out.size();
        pdf.write("xref\n0 6\n0000000000 65535 f \n");
        for (Integer offset : pdf.offsets) {
            pdf.write("%010d 00000 n \n".formatted(offset));
        }
        pdf.write("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF");
        return out.toByteArray();
    }

    private void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String xml(String text) {
        String value = text == null ? "" : text;
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static class ListWithOffsets {
        private final ByteArrayOutputStream out;
        private final java.util.List<Integer> offsets = new java.util.ArrayList<>();

        private ListWithOffsets(ByteArrayOutputStream out) {
            this.out = out;
        }

        private void obj(String value) throws IOException {
            offsets.add(out.size());
            write(value);
        }

        private void write(String value) throws IOException {
            out.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
