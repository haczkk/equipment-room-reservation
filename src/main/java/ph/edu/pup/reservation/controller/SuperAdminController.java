package ph.edu.pup.reservation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.pup.reservation.dto.CreateAdminDTO;
import ph.edu.pup.reservation.entity.AuditLog;
import ph.edu.pup.reservation.entity.RegistrationCode;
import ph.edu.pup.reservation.entity.User;
import ph.edu.pup.reservation.service.SuperAdminService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    public SuperAdminController(SuperAdminService superAdminService) {
        this.superAdminService = superAdminService;
    }

    @PostMapping("/codes/generate")
    public ResponseEntity<?> generateCode(@RequestParam String section, 
                                          @RequestParam(defaultValue = "superadmin@pup.edu.ph") String adminEmail) {
        RegistrationCode code = superAdminService.generateCodeForSection(section, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "code", code.getCode(),
                "assignedSection", code.getAssignedSection(),
                "message", "Successfully generated single-use code for " + code.getAssignedSection()
        ));
    }

    @GetMapping("/codes")
    public ResponseEntity<List<RegistrationCode>> getAllCodes() {
        return ResponseEntity.ok(superAdminService.getAllCodes());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> getAuditLogs() {
        return ResponseEntity.ok(superAdminService.getAuditLogs());
    }

    @GetMapping(value = "/audit-logs/export-csv", produces = "text/csv")
    public ResponseEntity<String> exportAuditLogsCsv() {
        List<AuditLog> logs = superAdminService.getAuditLogs();
        StringBuilder csv = new StringBuilder();
        csv.append("Log ID,Timestamp,Actor / Performed By,Action,Details\n");

        for (AuditLog log : logs) {
            String timeStr = log.getTimestamp() != null ? log.getTimestamp().toString().replace('T', ' ') : "";
            csv.append(log.getId()).append(",")
               .append("\"").append(timeStr).append("\",")
               .append("\"").append(log.getPerformedBy() != null ? log.getPerformedBy().replace("\"", "\"\"") : "SYSTEM").append("\",")
               .append("\"").append(log.getAction() != null ? log.getAction().replace("\"", "\"\"") : "").append("\",")
               .append("\"").append(log.getDetails() != null ? log.getDetails().replace("\"", "\"\"") : "").append("\"\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pup_audit_trail.csv\"")
                .body(csv.toString());
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestParam String adminEmail) {
        return ResponseEntity.ok(superAdminService.getAllUsers(adminEmail));
    }

    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdmin(@jakarta.validation.Valid @RequestBody CreateAdminDTO dto, @RequestParam String adminEmail) {
        User admin = superAdminService.createAdminAccount(dto, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Admin staff account created for " + admin.getEmail()
        ));
    }

    // Delete created Staff Administrator account
    @PostMapping("/users/{id}/delete")
    public ResponseEntity<?> deleteAdmin(@PathVariable Long id, @RequestParam String adminEmail) {
        superAdminService.deleteAdminAccount(id, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Staff Administrator account was permanently deleted."
        ));
    }

    @PostMapping("/users/{id}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long id, @RequestParam String adminEmail) {
        boolean active = superAdminService.toggleUserStatus(id, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "active", active,
                "message", "Account status updated to " + (active ? "ACTIVE" : "DEACTIVATED")
        ));
    }

    // Academic Year Rollover Endpoint
    @PostMapping("/reset-academic-year")
    public ResponseEntity<?> resetAcademicYear(@RequestParam String adminEmail) {
        String message = superAdminService.resetAcademicYear(adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message
        ));
    }
}