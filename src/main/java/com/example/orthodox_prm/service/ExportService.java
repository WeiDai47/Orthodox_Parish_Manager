package com.example.orthodox_prm.service;

import com.example.orthodox_prm.model.Parishioner;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExportService {

    public byte[] generateCsv(List<Parishioner> parishioners) {
        StringBuilder csv = new StringBuilder();
        csv.append("Last Name,First Name,Baptismal Name,Status,Phone,Email\n");

        for (Parishioner p : parishioners) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s\n",
                    p.getLastName(), p.getFirstName(), p.getBaptismalName(),
                    p.getStatus(), "N/A", "N/A")); // Add phone/email if in your model
        }
        return csv.toString().getBytes();
    }

    public byte[] generateWordDoc(List<Parishioner> parishioners) throws IOException {
        XWPFDocument document = new XWPFDocument();

        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText("Parish Directory - Master List");
        titleRun.setBold(true);
        titleRun.setFontSize(16);

        XWPFTable table = document.createTable();
        XWPFTableRow header = table.getRow(0);
        header.getCell(0).setText("Legal Name");
        header.addNewTableCell().setText("Baptismal Name");
        header.addNewTableCell().setText("Patron Saint");
        header.addNewTableCell().setText("Status");

        for (Parishioner p : parishioners) {
            XWPFTableRow row = table.createRow();
            row.getCell(0).setText(p.getFirstName() + " " + p.getLastName());
            row.getCell(1).setText(p.getBaptismalName());
            row.getCell(2).setText(p.getPatronSaint());
            row.getCell(3).setText(p.getStatus().toString());
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        document.close();
        return out.toByteArray();
    }
    // Logic to add to ExportService.java
    public void exportToExcel(List<Parishioner> parishioners, HttpServletResponse response) {
        // Uses Apache POI to create rows based on Parishioner fields:
        // FirstName, LastName, BaptismalName, Status, Household
    }

    public void exportToWord(List<Parishioner> parishioners, HttpServletResponse response) {
        // Uses Apache POI XWPF to create a formatted directory or table
    }


    // Inside ExportService.java
    public byte[] generateExcel(List<Parishioner> parishioners) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Parishioners");

            // Header Row
            Row header = sheet.createRow(0);
            String[] columns = {"Last Name", "First Name", "Baptismal Name", "Status", "Patron Saint", "Name Day"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }

            // Data Rows
            int rowIdx = 1;
            for (Parishioner p : parishioners) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getLastName());
                row.createCell(1).setCellValue(p.getFirstName());
                row.createCell(2).setCellValue(p.getBaptismalName());
                row.createCell(3).setCellValue(p.getStatus().toString());
                row.createCell(4).setCellValue(p.getPatronSaint());
                row.createCell(5).setCellValue(p.getNameDay() != null ? p.getNameDay().toString() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }


}