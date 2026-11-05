package ph.edu.pup.reservation.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.pup.reservation.dto.CreateAdminDTO;
import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.*;

import java.util.*;

@Service
public class SuperAdminService {

    private final RegistrationCodeRepository codeRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final ReservationRepository reservationRepository;
    private final ResourceItemRepository resourceRepository;

    public SuperAdminService(RegistrationCodeRepository codeRepository, 
                             AuditLogRepository auditLogRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             NotificationRepository notificationRepository,
                             ReservationRepository reservationRepository,
                             ResourceItemRepository resourceRepository) {
        this.codeRepository = codeRepository;
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationRepository = notificationRepository;
        this.reservationRepository = reservationRepository;
        this.resourceRepository = resourceRepository;
    }

    private void verifySuperAdminPrivileges(String adminEmail) {
        User user = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + adminEmail));
        if (user.getRole() != Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Access Denied: Super Administrator privileges required.");
        }
    }

    // 1. Generate section registration code
    @Transactional
    public RegistrationCode generateCodeForSection(String section, String adminEmail) {
        verifySuperAdminPrivileges(adminEmail);

        if (section == null || section.trim().isEmpty()) {
            throw new IllegalArgumentException("Section name cannot be empty.");
        }

        String cleanSection = section.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        String randomSuffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String generatedCode = "SEC-" + cleanSection + "-" + randomSuffix;

        RegistrationCode regCode = new RegistrationCode();
        regCode.setCode(generatedCode);
        regCode.setAssignedSection(section.trim());
        regCode.setUsed(false);

        RegistrationCode saved = codeRepository.save(regCode);
        logAction(adminEmail, "GENERATE_CODE", "Generated registration code " + generatedCode + " for section " + section.trim());
        return saved;
    }

    // 2. View all registration codes
    public List<RegistrationCode> getAllCodes() {
        return codeRepository.findAll();
    }

    // 3. View full audit trail
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    // 4. View all registered accounts
    public List<Map<String, Object>> getAllUsers(String adminEmail) {
        verifySuperAdminPrivileges(adminEmail);

        List<User> users = userRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("fullName", u.getFullName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole().name());
            map.put("section", u.getSection() != null ? u.getSection() : "N/A");
            map.put("active", u.isActive());
            result.add(map);
        }

        return result;
    }

    // 5. Create new Staff Administrator account
    @Transactional
    public User createAdminAccount(CreateAdminDTO dto, String superAdminEmail) {
        verifySuperAdminPrivileges(superAdminEmail);

        if (userRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        User admin = new User();
        admin.setFullName(dto.getFullName().trim());
        admin.setEmail(dto.getEmail().trim());
        admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setSection(null);
        admin.setActive(true);

        User saved = userRepository.save(admin);
        logAction(superAdminEmail, "CREATE_ADMIN", "Created new Staff Administrator account for " + saved.getEmail());
        return saved;
    }

    // 6. Delete created Staff Administrator account
    @Transactional
    public void deleteAdminAccount(Long userId, String superAdminEmail) {
        verifySuperAdminPrivileges(superAdminEmail);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        User caller = userRepository.findByEmail(superAdminEmail).orElseThrow();
        if (targetUser.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("You cannot delete your own Super Administrator account.");
        }

        if (targetUser.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot delete another Super Administrator account.");
        }

        if (targetUser.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Only created Staff Administrator accounts can be deleted here.");
        }

        String targetEmail = targetUser.getEmail();
        userRepository.delete(targetUser);

        logAction(superAdminEmail, "DELETE_ADMIN", "Deleted Staff Administrator account: " + targetEmail);
    }

    // 7. Toggle account status (Active <-> Deactivated)
    @Transactional
    public boolean toggleUserStatus(Long userId, String superAdminEmail) {
        verifySuperAdminPrivileges(superAdminEmail);

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found."));

        User caller = userRepository.findByEmail(superAdminEmail).orElseThrow();
        if (targetUser.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("You cannot deactivate your own Super Admin account.");
        }

        targetUser.setActive(!targetUser.isActive());
        userRepository.save(targetUser);

        String statusStr = targetUser.isActive() ? "ACTIVATED" : "DEACTIVATED";
        logAction(superAdminEmail, "TOGGLE_USER_STATUS", "Super Admin set account " + targetUser.getEmail() + " to " + statusStr);

        return targetUser.isActive();
    }

    // 8. Academic Year Rollover & System Reset
    @Transactional
    public String resetAcademicYear(String superAdminEmail) {
        verifySuperAdminPrivileges(superAdminEmail);

        // Clear all transactional logs and bookings
        notificationRepository.deleteAll();
        reservationRepository.deleteAll();

        // Clear expired registration codes from the past school year
        codeRepository.deleteAll();

        // Delete all graduating Class President accounts (preserves ADMIN and SUPER_ADMIN)
        List<User> presidents = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CLASS_PRESIDENT)
                .toList();
        userRepository.deleteAll(presidents);

        // Reset all facilities & equipment stock to full capacity (0 maintenance)
        List<ResourceItem> resources = resourceRepository.findAll();
        for (ResourceItem r : resources) {
            r.setMaintenanceQuantity(0);
            r.setAvailableQuantity(r.getTotalQuantity());
            r.setStatus(ResourceStatus.AVAILABLE);
        }
        resourceRepository.saveAll(resources);

        logAction(superAdminEmail, "ACADEMIC_YEAR_RESET", 
                  "Executed annual system rollover: Cleared president accounts, past reservations, and restored equipment stock.");

        return "Academic Year Rollover completed successfully. System is ready for the new school year.";
    }

    private void logAction(String user, String action, String details) {
        AuditLog log = new AuditLog();
        log.setPerformedBy(user);
        log.setAction(action);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}