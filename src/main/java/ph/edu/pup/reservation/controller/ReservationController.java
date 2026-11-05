package ph.edu.pup.reservation.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.pup.reservation.dto.ReservationRequestDTO;
import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.ReservationRepository;
import ph.edu.pup.reservation.repository.ResourceItemRepository;
import ph.edu.pup.reservation.repository.UserRepository;
import ph.edu.pup.reservation.service.ReservationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ResourceItemRepository resourceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public ReservationController(ReservationService reservationService, 
                                 ResourceItemRepository resourceRepository,
                                 ReservationRepository reservationRepository,
                                 UserRepository userRepository) {
        this.reservationService = reservationService;
        this.resourceRepository = resourceRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/resources")
    public ResponseEntity<List<ResourceItem>> getAllResources() {
        return ResponseEntity.ok(resourceRepository.findAll());
    }

    @GetMapping("/availability")
    public ResponseEntity<?> getAvailability(
            @RequestParam Long resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        ResourceItem resource = resourceRepository.findById(resourceId).orElse(null);
        if (resource == null) {
            throw new IllegalArgumentException("Resource not found.");
        }

        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.AUTO_APPROVED,
                ReservationStatus.APPROVED,
                ReservationStatus.PICKED_UP
        );

        List<Reservation> bookings = reservationRepository
                .findByResourceIdAndReservationDateAndStatusIn(resourceId, date, activeStatuses);

        List<Map<String, Object>> bookingSummaries = new ArrayList<>();
        for (Reservation b : bookings) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("startTime", b.getStartTime() != null ? b.getStartTime().toString() : "00:00:00");
            map.put("endTime", b.getEndTime() != null ? b.getEndTime().toString() : "00:00:00");
            map.put("purpose", b.getPurpose() != null ? b.getPurpose() : "Reserved");
            map.put("professor", b.getProfessorName() != null ? b.getProfessorName() : "Faculty");
            map.put("quantity", b.getRequestedQuantity());
            map.put("status", b.getStatus() != null ? b.getStatus().name() : "APPROVED");
            bookingSummaries.add(map);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("resourceId", resource.getId());
        response.put("resourceName", resource.getName());
        response.put("resourceType", resource.getType() != null ? resource.getType().name() : "ROOM");
        response.put("totalQuantity", resource.getTotalQuantity());

        // FIX: Calculate actual working capacity (Total minus broken units)
        int maintQty = resource.getMaintenanceQuantity();
        int workingCapacity = Math.max(0, resource.getTotalQuantity() - maintQty);
        response.put("maintenanceQuantity", maintQty);
        response.put("usableQuantity", workingCapacity);
        response.put("status", resource.getStatus() != null ? resource.getStatus().name() : "AVAILABLE");

        response.put("date", date.toString());
        response.put("operatingHours", "07:00 - 19:00");
        response.put("bookings", bookingSummaries);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/request")
    public ResponseEntity<?> submitReservation(@jakarta.validation.Valid @RequestBody ReservationRequestDTO dto) {
        Reservation saved = reservationService.createReservation(dto);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "reservationId", saved.getId(),
                "status", saved.getStatus(),
                "pickupToken", saved.getPickupToken() != null ? saved.getPickupToken() : "None (Pending Admin Review)",
                "message", saved.getStatus() == ReservationStatus.AUTO_APPROVED
                        ? "Reservation auto-approved! Present your pickup token to the admin staff." 
                        : "Scheduling conflict detected. Request sent to Admin for manual review."
        ));
    }

    @GetMapping("/my-reservations")
    public ResponseEntity<?> getMyReservations(@RequestParam Long presidentId) {
        List<Reservation> list = reservationService.getPresidentReservations(presidentId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Reservation r : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("resourceName", r.getResource() != null ? r.getResource().getName() : "Unknown");
            map.put("resourceType", r.getResource() != null ? r.getResource().getType().name() : "");
            map.put("reservationDate", r.getReservationDate().toString());
            map.put("startTime", r.getStartTime().toString());
            map.put("endTime", r.getEndTime().toString());
            map.put("purpose", r.getPurpose());
            map.put("professorName", r.getProfessorName());
            map.put("quantity", r.getRequestedQuantity());
            map.put("status", r.getStatus().name());
            map.put("pickupToken", r.getPickupToken());
            map.put("tokenExpiresAt", r.getTokenExpiresAt() != null ? r.getTokenExpiresAt().toString() : null);
            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable Long id, @RequestParam Long presidentId) {
        Reservation cancelled = reservationService.cancelReservationByPresident(id, presidentId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Reservation #" + cancelled.getId() + " was cancelled successfully."
        ));
    }

    @PostMapping("/test-expire-schedule")
    public ResponseEntity<?> testExpireSchedule() {
        User user = userRepository.findAll().stream().findFirst().orElse(null);
        ResourceItem resource = resourceRepository.findAll().stream().findFirst().orElse(null);

        if (user == null || resource == null) {
            throw new IllegalArgumentException("No users or resources found.");
        }

        Reservation res = new Reservation();
        res.setPresident(user);
        res.setResource(resource);
        res.setReservationDate(LocalDate.now());
        res.setStartTime(LocalTime.now());
        res.setEndTime(LocalTime.now().plusHours(1));
        res.setProfessorName("Test Professor");
        res.setPurpose("Scheduler Verification Test");
        res.setRequestedQuantity(1);
        res.setStatus(ReservationStatus.AUTO_APPROVED);
        res.setPickupToken("TCK-TEST-EXPIRE");
        res.setTokenExpiresAt(LocalDateTime.now().plusSeconds(10));
        
        Reservation saved = reservationRepository.save(res);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Test reservation #" + saved.getId() + " created! It expires in 10 seconds.",
            "expiresAt", saved.getTokenExpiresAt()
        ));
    }
}