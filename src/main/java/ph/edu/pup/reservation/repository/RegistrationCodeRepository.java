package ph.edu.pup.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ph.edu.pup.reservation.entity.RegistrationCode;
import java.util.Optional;

public interface RegistrationCodeRepository extends JpaRepository<RegistrationCode, Long> {
    Optional<RegistrationCode> findByCodeAndIsUsedFalse(String code);
}