package ph.edu.pup.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.pup.reservation.dto.ResourceRequestDTO;
import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final ReservationRepository reservationRepository;
    private final ResourceItemRepository resourceRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository; // <-- Added
    private final NotificationService notificationService;

    public AdminService(ReservationRepository reservationRepository,
                        ResourceItemRepository resourceRepository,
                        AuditLogRepository auditLogRepository,
                        UserRepository userRepository,
                        NotificationService notificationService) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    // Security Check: verifies caller is an Admin or Super Admin
    private void verifyAdminPrivileges(String adminEmail) {
        User user = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + adminEmail));
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Access Denied: Administrative privileges required.");
        }
    }

    public List<Reservation> getPendingReservations() {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .toList();
    }

    @Transactional
    public Reservation approveReservation(Long reservationId, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (res.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be approved.");
        }

        res.setStatus(ReservationStatus.APPROVED);
        res.setPickupToken("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        res.setTokenExpiresAt(res.getReservationDate().atTime(res.getStartTime()).plusMinutes(30));

        notificationService.sendNotification(
            res.getPresident(),
            "Reservation Approved!",
            "Your request for " + res.getResource().getName() + " on " + res.getReservationDate() + " was approved. Token: " + res.getPickupToken(),
            "SUCCESS"
        );

        logAction(adminEmail, "MANUAL_APPROVE", 
                  "Approved reservation ID #" + res.getId() + " for " + res.getResource().getName());

        return reservationRepository.save(res);
    }

    @Transactional
    public Reservation rejectReservation(Long reservationId, String adminEmail, String reason) {
        verifyAdminPrivileges(adminEmail);

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (res.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected.");
        }

        res.setStatus(ReservationStatus.REJECTED);

        notificationService.sendNotification(
            res.getPresident(),
            "Reservation Rejected",
            "Your request for " + res.getResource().getName() + " on " + res.getReservationDate() + " was rejected. Reason: " + reason,
            "DANGER"
        );
        
        logAction(adminEmail, "REJECT", "Rejected reservation ID #" + res.getId() + ". Reason: " + reason);

        return reservationRepository.save(res);
    }

    @Transactional
    public Reservation redeemTokenAndPickup(String token, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        Reservation res = reservationRepository.findByPickupTokenAndTokenRedeemedFalse(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or already redeemed pickup token."));

        if (LocalDateTime.now().isAfter(res.getTokenExpiresAt())) {
            res.setStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(res);
            logAction("SYSTEM", "AUTO_CANCEL", "Token " + token + " expired (no-show grace period ended).");
            throw new IllegalStateException("Grace period expired. Reservation has been cancelled.");
        }

        res.setTokenRedeemed(true);
        res.setStatus(ReservationStatus.PICKED_UP);

        ResourceItem resource = res.getResource();
        // Deduct borrowed quantity for ALL resources (Rooms & Equipment)
        int newQty = Math.max(0, resource.getAvailableQuantity() - res.getRequestedQuantity());
        resource.setAvailableQuantity(newQty);
        resourceRepository.save(resource);

        logAction(adminEmail, "PICKUP", "Verified token " + token + ". Released " + resource.getName() + " to " + res.getPresident().getFullName());
        return reservationRepository.save(res);
    }

    @Transactional
    public Reservation returnResource(Long reservationId, String adminEmail, boolean markedDamaged, Integer damagedQty) {
        verifyAdminPrivileges(adminEmail);

        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (res.getStatus() != ReservationStatus.PICKED_UP) {
            throw new IllegalStateException("Only currently PICKED_UP items can be marked as returned.");
        }

        res.setStatus(ReservationStatus.RETURNED);
        ResourceItem resource = res.getResource();
        int totalReturned = res.getRequestedQuantity();

        if (markedDamaged) {
            // If admin specified how many were damaged, use that; otherwise default to all returned units
            int broken = (damagedQty != null && damagedQty > 0) ? Math.min(damagedQty, totalReturned) : totalReturned;
            int good = totalReturned - broken;

            resource.setMaintenanceQuantity(resource.getMaintenanceQuantity() + broken);
            resource.setAvailableQuantity(resource.getAvailableQuantity() + good);

            if (resource.getMaintenanceQuantity() >= resource.getTotalQuantity()) {
                resource.setStatus(ResourceStatus.UNDER_MAINTENANCE);
            }

            logAction(adminEmail, "MAINTENANCE", broken + " unit(s) of " + resource.getName() + " marked damaged (" + good + " returned in good condition).");
        } else {
            // All units returned in good condition
            int newAvailable = Math.min(resource.getTotalQuantity() - resource.getMaintenanceQuantity(), resource.getAvailableQuantity() + totalReturned);
            resource.setAvailableQuantity(newAvailable);
            logAction(adminEmail, "RETURN", "All " + totalReturned + " unit(s) of " + resource.getName() + " returned in good condition.");
        }

        resourceRepository.save(resource);
        return reservationRepository.save(res);
    }

    @Transactional
    public ResourceItem createResource(ResourceRequestDTO dto, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Resource name cannot be empty.");
        }

        ResourceItem resource = new ResourceItem();
        resource.setName(dto.getName().trim());
        resource.setType(dto.getType());

        int qty = dto.getType() == ResourceType.ROOM ? 1 : Math.max(1, dto.getTotalQuantity());
        resource.setTotalQuantity(qty);
        resource.setAvailableQuantity(qty);
        resource.setStatus(ResourceStatus.AVAILABLE);

        ResourceItem saved = resourceRepository.save(resource);
        logAction(adminEmail, "CREATE_RESOURCE", "Added new " + dto.getType().name() + ": " + saved.getName() + " (Qty: " + qty + ")");
        return saved;
    }

    @Transactional
    public ResourceItem repairResourceUnits(Long resourceId, int unitsToRepair, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found."));

        if (resource.getMaintenanceQuantity() <= 0) {
            throw new IllegalArgumentException("No units of " + resource.getName() + " are currently in maintenance.");
        }

        int fixed = Math.min(unitsToRepair, resource.getMaintenanceQuantity());
        resource.setMaintenanceQuantity(resource.getMaintenanceQuantity() - fixed);
        resource.setAvailableQuantity(resource.getAvailableQuantity() + fixed);

        if (resource.getMaintenanceQuantity() < resource.getTotalQuantity()) {
            resource.setStatus(ResourceStatus.AVAILABLE);
        }

        logAction(adminEmail, "REPAIR_UNIT", fixed + " unit(s) of " + resource.getName() + " repaired and returned to available stock (" + resource.getMaintenanceQuantity() + " still in maintenance).");
        return resourceRepository.save(resource);
    }

    @Transactional
    public ResourceItem toggleMaintenance(Long resourceId, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found."));

        // If some units are in maintenance, repair 1 unit by default
        if (resource.getMaintenanceQuantity() > 0) {
            return repairResourceUnits(resourceId, 1, adminEmail);
        } else {
            // Send 1 unit (or the whole room) to maintenance
            int toBreak = resource.getType() == ResourceType.ROOM ? 1 : 1;
            resource.setMaintenanceQuantity(toBreak);
            resource.setAvailableQuantity(Math.max(0, resource.getAvailableQuantity() - toBreak));
            if (resource.getMaintenanceQuantity() >= resource.getTotalQuantity()) {
                resource.setStatus(ResourceStatus.UNDER_MAINTENANCE);
            }
            logAction(adminEmail, "SET_MAINTENANCE", toBreak + " unit(s) of " + resource.getName() + " placed under maintenance.");
            return resourceRepository.save(resource);
        }
    }

    @Transactional
    public ResourceItem setMaintenanceUnits(Long resourceId, int units, String adminEmail) {
        verifyAdminPrivileges(adminEmail);

        ResourceItem resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("Resource not found."));

        if (units <= 0) {
            throw new IllegalArgumentException("Units must be at least 1.");
        }

        if (units > resource.getAvailableQuantity()) {
            throw new IllegalArgumentException("Cannot place " + units + " unit(s) in maintenance. Only " + resource.getAvailableQuantity() + " unit(s) are currently in storage.");
        }

        resource.setMaintenanceQuantity(resource.getMaintenanceQuantity() + units);
        resource.setAvailableQuantity(resource.getAvailableQuantity() - units);

        if (resource.getMaintenanceQuantity() >= resource.getTotalQuantity()) {
            resource.setStatus(ResourceStatus.UNDER_MAINTENANCE);
        }

        logAction(adminEmail, "SET_MAINTENANCE", units + " unit(s) of " + resource.getName() + " placed under maintenance (" + resource.getMaintenanceQuantity() + " total in maintenance).");
        return resourceRepository.save(resource);
    }

    public Map<String, Object> getDashboardStats() {
        long pending = reservationRepository.countByStatus(ReservationStatus.PENDING);
        long activePickups = reservationRepository.countByStatus(ReservationStatus.PICKED_UP);

        // Sum of all individual damaged units across the entire campus
        long totalMaintenanceUnits = 0;
        for (ResourceItem r : resourceRepository.findAll()) {
            totalMaintenanceUnits += r.getMaintenanceQuantity();
        }

        long totalResources = resourceRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", pending);
        stats.put("activePickups", activePickups);
        stats.put("maintenance", totalMaintenanceUnits);
        stats.put("totalResources", totalResources);
        return stats;
    }

    private void logAction(String user, String action, String details) {
        AuditLog log = new AuditLog();
        log.setPerformedBy(user);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    public List<Reservation> getActivePickups() {
        return reservationRepository.findByStatus(ReservationStatus.PICKED_UP);
    }
}