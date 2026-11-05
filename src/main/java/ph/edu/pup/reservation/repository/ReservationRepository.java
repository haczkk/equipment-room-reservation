package ph.edu.pup.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.pup.reservation.entity.Reservation;
import ph.edu.pup.reservation.entity.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
    List<Reservation> findByResourceIdAndReservationDateAndStatusIn(
        Long resourceId, 
        LocalDate reservationDate, 
        List<ReservationStatus> statuses
    );

    Optional<Reservation> findByPickupTokenAndTokenRedeemedFalse(String token);

    List<Reservation> findByStatusInAndTokenRedeemedFalseAndTokenExpiresAtBefore(
        List<ReservationStatus> statuses, 
        LocalDateTime now
    );

    // NEW: Get all reservations created by a specific class president
    List<Reservation> findByPresidentIdOrderByCreatedAtDesc(Long presidentId);

    // Count reservations by status
    long countByStatus(ReservationStatus status);

    List<Reservation> findByStatus(ReservationStatus status);
}