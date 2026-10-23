package com.daniellaera.authservice;

import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/** The application as it starts: demo accounts only with the local profile (db/dev), never without it. */
class DemoUsersProfileITTest {

    @Nested
    @SpringBootTest
    @Import(TestcontainersConfiguration.class)
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    class DefaultProfile {

        @Autowired private UserRepository userRepository;
        @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;

        @Test
        void hasNoDemoAccounts() {
            assertThat(userRepository.findByEmail("daniel@example.com")).isEmpty();
            assertThat(userRepository.findByEmail("admin@example.com")).isEmpty();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("local")
    @Import(TestcontainersConfiguration.class)
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    class LocalProfile {

        @Autowired private UserRepository userRepository;
        @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;

        @Test
        void seedsBothDemoAccounts() {
            assertThat(userRepository.findByEmail("daniel@example.com")).map(User::getRole).map(Enum::name)
                    .contains("USER");
            assertThat(userRepository.findByEmail("admin@example.com")).map(User::getRole).map(Enum::name)
                    .contains("ADMIN");
        }
    }
}
