package ph.edu.pup.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.pup.reservation.entity.Notification;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long userId);
    long countByRecipientIdAndIsReadFalse(Long userId);
}