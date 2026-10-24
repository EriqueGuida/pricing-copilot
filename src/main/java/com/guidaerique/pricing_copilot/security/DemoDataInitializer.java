package com.guidaerique.pricing_copilot.security;

import com.guidaerique.pricing_copilot.catalog.domain.Category;
import com.guidaerique.pricing_copilot.catalog.domain.Vertical;
import com.guidaerique.pricing_copilot.catalog.repository.CategoryRepository;
import com.guidaerique.pricing_copilot.security.domain.AppUser;
import com.guidaerique.pricing_copilot.security.domain.Role;
import com.guidaerique.pricing_copilot.security.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@Profile("dev") // Roda apenas quando você liga o projeto localmente (não afeta os testes!)
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    @Bean
    public CommandLineRunner initDemoData(
            AppUserRepository userRepository,
            CategoryRepository categoryRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> {
            if (!userRepository.existsByEmail("admin@copiloto.com")) {
                userRepository.save(new AppUser(
                        "admin@copiloto.com",
                        passwordEncoder.encode("Admin@123"),
                        Role.ADMIN
                ));
                log.info("Usuário de demonstração criado: admin@copiloto.com / Admin@123");
            }

            if (categoryRepository.count() == 0) {
                categoryRepository.save(new Category(
                        "Hardware & GPUs",
                        Vertical.COMPUTER_GAMING,
                        new BigDecimal("0.1500"),
                        new BigDecimal("0.5000"),
                        new BigDecimal("0.2000")
                ));
                log.info("Categoria de demonstração criada: Hardware & GPUs (ID 1)");
            }
        };
    }
}