package com.awesome.testing.controller;

import com.awesome.testing.dto.sjp.ExtensionDTO;
import com.awesome.testing.service.OspsService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/osps")
@Api(tags = "osps")
@RequiredArgsConstructor
public class OspsController {

    private final OspsService ospsService;

    @GetMapping(value = "/{length}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${OspsController.getWords()}", authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public List<String> getWords(@ApiParam("length") @PathVariable String length) {
        return ospsService.getWords(Integer.parseInt(length));
    }

    @GetMapping(value = "/extensions/{length}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${OspsController.getWords()}", authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public List<ExtensionDTO> getExtensions(@ApiParam("length") @PathVariable String length) throws IOException, DocumentException {

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream("OSPS" + length + ".pdf"));

        document.open();
        BaseFont bf = BaseFont.createFont("Helvetica", "ISO-8859-2", false);
        Font modified_font = new Font(bf, 9, Font.NORMAL);
        PdfPTable table = new PdfPTable(3);
        addTableHeader(table, modified_font);


        List<String> words = ospsService.getWords(Integer.parseInt(length));
        List<String> longerWords = ospsService.getWords(Integer.parseInt(length) + 1);

        List<ExtensionDTO> entries = words.stream().map(word -> ExtensionDTO.builder()
                .beforeExtensions(getLeftExtensions(word, longerWords))
                .word(word.toUpperCase())
                .afterExtensions(getRightExtensions(word, longerWords))
                .build()
        ).collect(Collectors.toList());

        entries.forEach(entry ->
                Stream.of(entry.getBeforeExtensions(), entry.getWord(), entry.getAfterExtensions())
                        .forEach(cell -> {
                            PdfPCell header = new PdfPCell();
                            header.setPhrase(new Phrase(cell, modified_font));
                            table.addCell(header);
                        }));
        document.add(table);
        document.close();
        return entries;
    }

    private String getRightExtensions(String word, List<String> wordsBefore) {
        return wordsBefore.stream().filter(it -> it.startsWith(word))
                .map(it -> it.substring(it.length() - 1))
                .map(String::toUpperCase)
                .collect(Collectors.joining(" "));
    }

    private String getLeftExtensions(String word, List<String> wordsAfter) {
        return wordsAfter.stream().filter(it -> it.endsWith(word))
                .map(it -> it.substring(0, 1))
                .map(String::toUpperCase)
                .collect(Collectors.joining(" "));
    }

    private void addTableHeader(PdfPTable table, Font modified_font) {
        Stream.of("przedłużki lewe", "słowo", "przedłużki prawe")
                .forEach(columnTitle -> {
                    PdfPCell header = new PdfPCell();
                    header.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header.setBorderWidth(2);
                    header.setPhrase(new Phrase(columnTitle, modified_font));
                    table.addCell(header);
                });
    }

}
