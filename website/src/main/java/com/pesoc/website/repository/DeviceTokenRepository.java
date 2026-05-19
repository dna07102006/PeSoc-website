package com.pesoc.website.repository;

import com.pesoc.website.model.DeviceToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    boolean existsByToken(String token);

    Optional<DeviceToken> findByToken(String token);

    void deleteByToken(String token);
}