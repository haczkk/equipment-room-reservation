package ph.edu.pup.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.pup.reservation.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsBySectionAndActiveTrue(String section);
}