package ph.edu.pup.reservation.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.pup.reservation.entity.AuditLog;
import ph.edu.pup.reservation.entity.Reservation;
import ph.edu.pup.reservation.entity.ReservationStatus;
import ph.edu.pup.reservation.repository.AuditLogRepository;
import ph.edu.pup.reservation.repository.ReservationRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationScheduler {

    private final ReservationRepository reservationRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;

    public ReservationScheduler(ReservationRepository reservationRepository, 
                                AuditLogRepository auditLogRepository,
                                NotificationService notificationService) {
        this.reservationRepository = reservationRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
    }

    // Runs automatically every 60 seconds (60000 milliseconds)
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void autoCancelUnclaimedReservations() {
        LocalDateTime now = LocalDateTime.now();

        List<ReservationStatus> approvedStatuses = List.of(
                ReservationStatus.AUTO_APPROVED,
                ReservationStatus.APPROVED
        );

        List<Reservation> expiredReservations = reservationRepository
                .findByStatusInAndTokenRedeemedFalseAndTokenExpiresAtBefore(approvedStatuses, now);

        if (!expiredReservations.isEmpty()) {
            for (Reservation res : expiredReservations) {
                // 1. Update status to CANCELLED
                res.setStatus(ReservationStatus.CANCELLED);
                reservationRepository.save(res);

                // 2. Record cancellation in Audit Log (as required by flowchart)
                AuditLog log = new AuditLog();
                log.setPerformedBy("SYSTEM_SCHEDULER");
                log.setAction("AUTO_CANCEL");
                log.setDetails("Reservation #" + res.getId() + " for " + res.getResource().getName() + 
                               " was auto-cancelled due to unclaimed token within the grace period.");
                auditLogRepository.save(log);

                notificationService.sendNotification(
                    res.getPresident(),
                    "Reservation Auto-Cancelled",
                    "Reservation #" + res.getId() + " for " + res.getResource().getName() + " was cancelled due to no-show within the 30-min grace period.",
                    "WARNING"
                );

                System.out.println(">>> [AUTO-CANCEL] Reservation #" + res.getId() + " was automatically cancelled (Grace period expired).");
            }
        }
    }
}