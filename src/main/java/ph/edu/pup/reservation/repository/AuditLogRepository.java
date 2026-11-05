package ph.edu.pup.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.pup.reservation.entity.AuditLog;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByTimestampDesc();
}