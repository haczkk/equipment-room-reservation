package ph.edu.pup.reservation.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ph.edu.pup.reservation.entity.ResourceItem;
import ph.edu.pup.reservation.entity.ResourceStatus;
import ph.edu.pup.reservation.entity.ResourceType;

import java.util.List;
import java.util.Optional;

public interface ResourceItemRepository extends JpaRepository<ResourceItem, Long> {
    List<ResourceItem> findByType(ResourceType type);

    long countByStatus(ResourceStatus status);

    // Concurrency Shield: Issues "SELECT ... FOR UPDATE" in MySQL
    // Prevents race conditions and double-booking across concurrent threads
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResourceItem r WHERE r.id = :id")
    Optional<ResourceItem> findWithLockById(@Param("id") Long id);
}