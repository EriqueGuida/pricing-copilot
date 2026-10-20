package com.guidaerique.pricing_copilot;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-anotação dos testes que sobem o contexto Spring com MySQL real
 * (Testcontainers) e aplicam as migrations Flyway de verdade.
 *
 * Padroniza o perfil "test" e a configuração do container: todas as classes
 * compartilham o MESMO contexto em cache e, portanto, um único container
 * MySQL por execução do Maven.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
