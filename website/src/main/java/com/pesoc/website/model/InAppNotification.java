package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "in_app_notifications")
public class InAppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String body;
    private String url;
    private String receiverUsername;
    private boolean isRead = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}