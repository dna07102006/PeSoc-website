package com.pesoc.website.repository;

import com.pesoc.website.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByUserIdAndTargetIdAndTargetType(Long userId, String targetId, String targetType);
    boolean existsByUserIdAndTargetIdAndTargetType(Long userId, String targetId, String targetType);

    List<Subscription> findByTargetIdAndTargetType(String targetId, String targetType);
}