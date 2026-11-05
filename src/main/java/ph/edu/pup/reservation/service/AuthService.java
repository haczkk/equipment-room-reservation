package ph.edu.pup.reservation.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.pup.reservation.dto.LoginRequestDTO;
import ph.edu.pup.reservation.dto.PresidentRegisterDTO;
import ph.edu.pup.reservation.entity.AuditLog;
import ph.edu.pup.reservation.entity.RegistrationCode;
import ph.edu.pup.reservation.entity.Role;
import ph.edu.pup.reservation.entity.User;
import ph.edu.pup.reservation.repository.AuditLogRepository;
import ph.edu.pup.reservation.repository.RegistrationCodeRepository;
import ph.edu.pup.reservation.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RegistrationCodeRepository codeRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, 
                       RegistrationCodeRepository codeRepository, 
                       AuditLogRepository auditLogRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. President Registration using Single-Use Code with BCrypt hashing
    @Transactional
    public String registerPresident(PresidentRegisterDTO dto) {
        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        RegistrationCode regCode = codeRepository.findByCodeAndIsUsedFalse(dto.getRegistrationCode())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or already used registration code."));

        if (userRepository.existsBySectionAndActiveTrue(regCode.getAssignedSection())) {
            throw new IllegalArgumentException("An active Class President account already exists for section " + regCode.getAssignedSection());
        }

        User president = new User();
        president.setFullName(dto.getFullName());
        president.setEmail(dto.getEmail());
        // Hash the password with BCrypt before persisting to MySQL!
        president.setPassword(passwordEncoder.encode(dto.getPassword()));
        president.setRole(Role.CLASS_PRESIDENT);
        president.setSection(regCode.getAssignedSection());
        president.setActive(true);

        userRepository.save(president);

        regCode.setUsed(true);
        codeRepository.save(regCode);

        // Record in audit log
        AuditLog log = new AuditLog();
        log.setPerformedBy(president.getEmail());
        log.setAction("REGISTER");
        log.setDetails("Registered as Class President for section " + regCode.getAssignedSection());
        auditLogRepository.save(log);

        return "Successfully registered as Class President for section " + regCode.getAssignedSection();
    }

    // 2. User Login with BCrypt verification
    @Transactional
    public Map<String, Object> login(LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        // Use passwordEncoder.matches to check raw password against the BCrypt hash
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        if (!user.isActive()) {
            throw new IllegalArgumentException("This account is currently deactivated.");
        }

        // Record in audit log
        AuditLog log = new AuditLog();
        log.setPerformedBy(user.getEmail());
        log.setAction("LOGIN");
        log.setDetails("User logged in successfully (Role: " + user.getRole().name() + ").");
        auditLogRepository.save(log);

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("fullName", user.getFullName());
        userData.put("email", user.getEmail());
        userData.put("role", user.getRole().name());
        userData.put("section", user.getSection() != null ? user.getSection() : "N/A");

        return userData;
    }

    @Transactional
    public String changePassword(ph.edu.pup.reservation.dto.ChangePasswordDTO dto) {
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long.");
        }

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // Verify current password against existing BCrypt hash
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password.");
        }

        // Prevent setting the exact same password
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password cannot be the same as your current password.");
        }

        // Hash and save new password
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);

        // Record in audit log
        AuditLog log = new AuditLog();
        log.setPerformedBy(user.getEmail());
        log.setAction("CHANGE_PASSWORD");
        log.setDetails("User changed their account password.");
        auditLogRepository.save(log);

        return "Password successfully updated.";
    }
}