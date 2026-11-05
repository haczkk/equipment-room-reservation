package ph.edu.pup.reservation.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.pup.reservation.dto.ResourceRequestDTO;
import ph.edu.pup.reservation.entity.Reservation;
import ph.edu.pup.reservation.entity.ResourceItem;
import ph.edu.pup.reservation.repository.ResourceItemRepository;
import ph.edu.pup.reservation.service.AdminService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final ResourceItemRepository resourceRepository;

    public AdminController(AdminService adminService, ResourceItemRepository resourceRepository) {
        this.adminService = adminService;
        this.resourceRepository = resourceRepository;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<?> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/reservations/pending")
    public ResponseEntity<List<Reservation>> getPending() {
        return ResponseEntity.ok(adminService.getPendingReservations());
    }

    @PostMapping("/reservations/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestParam String adminEmail) {
        Reservation res = adminService.approveReservation(id, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Reservation approved! Pickup token generated.",
                "generatedPickupToken", res.getPickupToken()
        ));
    }

    @PostMapping("/reservations/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, 
                                    @RequestParam String adminEmail, 
                                    @RequestParam String reason) {
        adminService.rejectReservation(id, adminEmail, reason);
        return ResponseEntity.ok(Map.of("success", true, "message", "Reservation rejected."));
    }

    @PostMapping("/pickup")
    public ResponseEntity<?> pickup(@RequestParam String token, @RequestParam String adminEmail) {
        Reservation res = adminService.redeemTokenAndPickup(token, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token verified! Key/equipment released.",
                "status", res.getStatus()
        ));
    }

    @PostMapping("/reservations/{id}/return")
    public ResponseEntity<?> returnItem(
            @PathVariable Long id, 
            @RequestParam String adminEmail, 
            @RequestParam(defaultValue = "false") boolean damaged,
            @RequestParam(required = false) Integer damagedQuantity) {
        Reservation res = adminService.returnResource(id, adminEmail, damaged, damagedQuantity);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Resource successfully returned.",
                "status", res.getStatus()
        ));
    }

    @PostMapping("/resources/{id}/repair")
    public ResponseEntity<?> repairUnits(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int units,
            @RequestParam String adminEmail) {
        ResourceItem item = adminService.repairResourceUnits(id, units, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", units + " unit(s) repaired. (" + item.getMaintenanceQuantity() + " still in maintenance)",
                "resource", item
        ));
    }

    @PostMapping("/resources")
    public ResponseEntity<?> createResource(@jakarta.validation.Valid @RequestBody ResourceRequestDTO dto,
                                            @RequestParam String adminEmail) {
        ResourceItem created = adminService.createResource(dto, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully added " + created.getName() + " to campus inventory!",
                "resource", created
        ));
    }

    @PostMapping("/resources/{id}/toggle-maintenance")
    public ResponseEntity<?> toggleMaintenance(@PathVariable Long id, @RequestParam String adminEmail) {
        ResourceItem updated = adminService.toggleMaintenance(id, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", updated.getName() + " status updated to " + updated.getStatus().name(),
                "status", updated.getStatus().name()
        ));
    }

    @PostMapping("/resources/{id}/set-maintenance")
    public ResponseEntity<?> setMaintenance(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int units,
            @RequestParam String adminEmail) {
        ResourceItem item = adminService.setMaintenanceUnits(id, units, adminEmail);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", units + " unit(s) of " + item.getName() + " moved to maintenance.",
                "resource", item
        ));
    }

    @GetMapping(value = "/inventory/export-csv", produces = "text/csv")
    public ResponseEntity<String> exportInventoryCsv() {
        List<ResourceItem> resources = resourceRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("Resource ID,Facility / Equipment Name,Type,Total Quantity,Available Quantity,Status\n");

        for (ResourceItem r : resources) {
            csv.append(r.getId()).append(",")
               .append("\"").append(r.getName().replace("\"", "\"\"")).append("\",")
               .append(r.getType().name()).append(",")
               .append(r.getTotalQuantity()).append(",")
               .append(r.getAvailableQuantity()).append(",")
               .append(r.getStatus().name()).append("\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pup_inventory_report.csv\"")
                .body(csv.toString());
    }

    @GetMapping("/reservations/active-pickups")
    public ResponseEntity<?> getActivePickups() {
        List<Reservation> list = adminService.getActivePickups();

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Reservation r : list) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", r.getId());
            map.put("presidentName", r.getPresident() != null ? r.getPresident().getFullName() : "N/A");
            map.put("section", r.getPresident() != null ? r.getPresident().getSection() : "N/A");
            map.put("resourceName", r.getResource() != null ? r.getResource().getName() : "Unknown");
            map.put("resourceType", r.getResource() != null ? r.getResource().getType().name() : "");
            map.put("quantity", r.getRequestedQuantity());
            map.put("reservationDate", r.getReservationDate().toString());
            map.put("startTime", r.getStartTime().toString());
            map.put("endTime", r.getEndTime().toString());
            map.put("pickupToken", r.getPickupToken());
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }
}