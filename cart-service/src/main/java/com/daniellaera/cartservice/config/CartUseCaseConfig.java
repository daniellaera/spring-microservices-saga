package com.daniellaera.cartservice.config;

import com.daniellaera.cartservice.application.ManageCartService;
import com.daniellaera.cartservice.domain.port.out.CartRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The use cases are a plain class: one bean, serving every cart port. */
@Configuration
public class CartUseCaseConfig {

    @Bean
    ManageCartService manageCartService(CartRepository cartRepository) {
        return new ManageCartService(cartRepository);
    }
}
