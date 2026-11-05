package ph.edu.pup.reservation.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.pup.reservation.dto.ReservationRequestDTO;
import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceItemRepository resourceRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    // Campus operating hours: 07:00 AM to 07:00 PM
    private static final LocalTime CAMPUS_OPEN = LocalTime.of(7, 0);
    private static final LocalTime CAMPUS_CLOSE = LocalTime.of(19, 0);

    public ReservationService(ReservationRepository reservationRepository,
                              ResourceItemRepository resourceRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository) {
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public Reservation createReservation(ReservationRequestDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime reservationStart = dto.getReservationDate().atTime(dto.getStartTime());

        // 1. Check if the date or timeslot has already passed
        if (reservationStart.isBefore(now)) {
            throw new IllegalArgumentException("Cannot submit a reservation for a date or timeslot that has already passed.");
        }

        // 2. Booking must be within the next 7 days
        if (dto.getReservationDate().isAfter(today.plusDays(7))) {
            throw new IllegalArgumentException("Reservations must be within the next 7 days.");
        }

        // 3. Validation: Booking must follow campus operating hours (07:00 - 19:00)
        if (dto.getStartTime().isBefore(CAMPUS_OPEN) || dto.getEndTime().isAfter(CAMPUS_CLOSE)) {
            throw new IllegalArgumentException("Reservations must be within campus operating hours (07:00 - 19:00).");
        }
        if (!dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }

        // 3. Find User & Resource
        User president = userRepository.findById(dto.getPresidentId())
                .orElseThrow(() -> new IllegalArgumentException("Class President account not found."));

        ResourceItem resource = resourceRepository.findById(dto.getResourceId())
                .orElseThrow(() -> new IllegalArgumentException("Resource not found."));

        if (resource.getStatus() == ResourceStatus.UNDER_MAINTENANCE) {
            throw new IllegalArgumentException("This resource is currently under maintenance.");
        }

        // 4. Check for overlapping bookings
        List<ReservationStatus> activeStatuses = List.of(
                ReservationStatus.AUTO_APPROVED,
                ReservationStatus.APPROVED,
                ReservationStatus.PICKED_UP
        );

        List<Reservation> existingBookings = reservationRepository
                .findByResourceIdAndReservationDateAndStatusIn(resource.getId(), dto.getReservationDate(), activeStatuses);

        boolean hasConflict = false;

        if (resource.getType() == ResourceType.ROOM) {
            // Room conflict: Any overlap means conflict
            for (Reservation r : existingBookings) {
                if (timesOverlap(dto.getStartTime(), dto.getEndTime(), r.getStartTime(), r.getEndTime())) {
                    hasConflict = true;
                    break;
                }
            }
        } else {
            // Equipment conflict: Sum overlapping units and check remaining stock
            int bookedQuantityInSlot = 0;
            for (Reservation r : existingBookings) {
                if (timesOverlap(dto.getStartTime(), dto.getEndTime(), r.getStartTime(), r.getEndTime())) {
                    bookedQuantityInSlot += r.getRequestedQuantity();
                }
            }
            int workingCapacity = resource.getTotalQuantity() - resource.getMaintenanceQuantity();
            if ((workingCapacity - bookedQuantityInSlot) < dto.getRequestedQuantity()) {
                hasConflict = true;
            }
        }

        // 5. Create reservation based on flowchart outcome
        Reservation res = new Reservation();
        res.setPresident(president);
        res.setResource(resource);
        res.setReservationDate(dto.getReservationDate());
        res.setStartTime(dto.getStartTime());
        res.setEndTime(dto.getEndTime());
        res.setProfessorName(dto.getProfessorName());
        res.setPurpose(dto.getPurpose());
        res.setRequestedQuantity(resource.getType() == ResourceType.ROOM ? 1 : dto.getRequestedQuantity());

        if (!hasConflict) {
            // Auto-Approve branch
            res.setStatus(ReservationStatus.AUTO_APPROVED);
            // Generate unguessable 8-character token, e.g. "TCK-A94D2B81"
            res.setPickupToken("TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            // Token grace period: up to 30 mins after start time
            res.setTokenExpiresAt(dto.getReservationDate().atTime(dto.getStartTime()).plusMinutes(30));

            logAction(president.getEmail(), "AUTO_APPROVE", 
                      "Auto-approved reservation for " + resource.getName() + " on " + dto.getReservationDate());
        } else {
            // Conflict branch -> Needs Admin manual review
            res.setStatus(ReservationStatus.PENDING);
            res.setPickupToken(null); // No token until admin approves

            logAction(president.getEmail(), "PENDING_REVIEW", 
                      "Conflict detected. Routed to Admin for " + resource.getName() + " on " + dto.getReservationDate());
        }

        return reservationRepository.save(res);
    }

    private boolean timesOverlap(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    private void logAction(String user, String action, String details) {
        AuditLog log = new AuditLog();
        log.setPerformedBy(user);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    // 1. Get reservation history for a president
    public List<Reservation> getPresidentReservations(Long presidentId) {
        return reservationRepository.findByPresidentIdOrderByCreatedAtDesc(presidentId);
    }

    // 2. Allow president to cancel their own reservation (before pickup)
    @Transactional
    public Reservation cancelReservationByPresident(Long reservationId, Long presidentId) {
        Reservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));

        if (!res.getPresident().getId().equals(presidentId)) {
            throw new IllegalArgumentException("Unauthorized: You can only cancel your own reservations.");
        }

        if (res.getStatus() == ReservationStatus.PICKED_UP || 
            res.getStatus() == ReservationStatus.RETURNED || 
            res.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel a reservation that is already " + res.getStatus());
        }

        res.setStatus(ReservationStatus.CANCELLED);
        Reservation saved = reservationRepository.save(res);

        logAction(res.getPresident().getEmail(), "PRESIDENT_CANCEL", 
                  "President cancelled reservation #" + res.getId() + " for " + res.getResource().getName());

        return saved;
    }
}