package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // Người bấm chuông

    private String targetId;   // ID của giải đấu (vd: "winter") HOẶC username của cầu thủ (vd: "tuan")
    private String targetType; // Để phân biệt: "TOURNAMENT" hoặc "PLAYER"

    // Sếp tự tạo Getter/Setter nhé...
}