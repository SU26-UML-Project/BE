package su26.uml.be.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import su26.uml.be.entity.Role;
import su26.uml.be.entity.User;
import su26.uml.be.enums.UserStatus;
import su26.uml.be.repository.RoleRepository;
import su26.uml.be.repository.UserRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Order(2)
public class DataInitializer implements CommandLineRunner {

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Initializing sample data...");

        // 1. Initialize Roles
        Role adminRole = initRole("ADMIN", "System Administrator Role");
        Role userRole = initRole("USER", "Standard Application User Role");

        // 2. Initialize Admin User
        initAdminUser(adminRole);

        log.info("Data initialization completed.");
    }

    private Role initRole(String roleName, String description) {
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .roleName(roleName)
                            .description(description)
                            .build();
                    log.info("Created role: {}", roleName);
                    return roleRepository.save(newRole);
                });
    }

    private void initAdminUser(Role adminRole) {
        String adminEmail = "admin@gmail.com";
        if (!userRepository.existsByEmail(adminEmail)) {
            User adminUser = User.builder()
                    .email(adminEmail)
                    .username("admin")
                    .password(passwordEncoder.encode("Password123"))
                    .fullName("System Administrator")
                    .phone("0123456789")
                    .status(UserStatus.ACTIVE)
                    .role(adminRole)
                    .build();
            userRepository.save(adminUser);
            log.info("Created sample admin user: {}", adminEmail);
        }
    }
}
