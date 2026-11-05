package ph.edu.pup.reservation.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import ph.edu.pup.reservation.entity.*;
import ph.edu.pup.reservation.repository.*;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ResourceItemRepository resourceRepository;
    private final RegistrationCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, 
                           ResourceItemRepository resourceRepository, 
                           RegistrationCodeRepository codeRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Upgrade any existing plain-text passwords to BCrypt hashes
        userRepository.findAll().forEach(user -> {
            if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                userRepository.save(user);
                System.out.println(">>> Upgraded password for " + user.getEmail() + " to secure BCrypt hash!");
            }
        });

        // 1. Create Super Admin if not present
        if (userRepository.findByEmail("superadmin@pup.edu.ph").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setFullName("System Super Administrator");
            superAdmin.setEmail("superadmin@pup.edu.ph");
            superAdmin.setPassword(passwordEncoder.encode("admin123"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            userRepository.save(superAdmin);
        }

        // 2. Create Staff Admin if not present
        if (userRepository.findByEmail("admin@pup.edu.ph").isEmpty()) {
            User staffAdmin = new User();
            staffAdmin.setFullName("Campus Admin Staff");
            staffAdmin.setEmail("admin@pup.edu.ph");
            staffAdmin.setPassword(passwordEncoder.encode("admin123"));
            staffAdmin.setRole(Role.ADMIN);
            userRepository.save(staffAdmin);
        }

        // 3. Create sample resources and initial code if database is fresh
        if (resourceRepository.count() == 0) {
            RegistrationCode code1 = new RegistrationCode();
            code1.setCode("SEC-BSIT21-A1B2");
            code1.setAssignedSection("BSIT 2-1");
            codeRepository.save(code1);

            ResourceItem room1 = new ResourceItem();
            room1.setName("AVR - Audio Visual Room");
            room1.setType(ResourceType.ROOM);
            room1.setTotalQuantity(1);
            room1.setAvailableQuantity(1);
            room1.setStatus(ResourceStatus.AVAILABLE);
            resourceRepository.save(room1);

            ResourceItem room2 = new ResourceItem();
            room2.setName("Computer Laboratory 1");
            room2.setType(ResourceType.ROOM);
            room2.setTotalQuantity(1);
            room2.setAvailableQuantity(1);
            room2.setStatus(ResourceStatus.AVAILABLE);
            resourceRepository.save(room2);

            ResourceItem projector = new ResourceItem();
            projector.setName("HDMI Epson Projector");
            projector.setType(ResourceType.EQUIPMENT);
            projector.setTotalQuantity(5);
            projector.setAvailableQuantity(5);
            projector.setStatus(ResourceStatus.AVAILABLE);
            resourceRepository.save(projector);

            System.out.println(">>> Sample test data successfully initialized! <<<");
        }
    }
}