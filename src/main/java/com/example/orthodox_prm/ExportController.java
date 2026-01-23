package com.example.orthodox_prm;

import org.springframework.ui.Model;
import com.example.orthodox_prm.Enum.MaritalStatus;
import com.example.orthodox_prm.Enum.MembershipStatus;
import com.example.orthodox_prm.model.Parishioner;
import com.example.orthodox_prm.repository.ParishionerRepository;
import com.example.orthodox_prm.service.ExportService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

@Controller
@RequestMapping("/export")
@PreAuthorize("hasAnyRole('PRIEST','SECRETARY')")
public class ExportController {

    private final ParishionerRepository parishionerRepository;
    private final ExportService exportService;

    public ExportController(ParishionerRepository parishionerRepository, ExportService exportService) {
        this.parishionerRepository = parishionerRepository;
        this.exportService = exportService;
    }

    @GetMapping("/options")
    public String showExportOptions(Model model) {
        model.addAttribute("parishioners", parishionerRepository.findAll(Sort.by("lastName")));
        return "export-options";
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateExport(@ModelAttribute ParishionerFilterCriteria criteria) {
        try {
            // 1. Find the people using your existing Specification logic
            // This automatically handles the DEPARTED status if it's in the criteria
            List<Parishioner> list = parishionerRepository.findAll(ParishionerSpecification.filterBy(criteria));

            // 2. Set the filename
            String filename = (criteria.getStatus() == MembershipStatus.DEPARTED) ? "Departed_List" : "Parish_Report";

            // 3. Generate the file
            byte[] data;
            String contentType;

            if ("excel".equals(criteria.getFormat())) {
                data = exportService.generateExcel(list);
                filename += ".xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                data = exportService.generateWordDoc(list);
                filename += ".docx";
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.status(500).body(("Error generating export file: " + e.getMessage()).getBytes());
        }
    }

}
