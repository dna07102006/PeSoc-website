package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Table(name = "pesoctok_videos")
public class PesocTokVideo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String youtubeUrl; // URL gốc người dùng nhập

    @Column(nullable = false)
    private String youtubeId; // ID video YouTube đã trích xuất

    @Column(columnDefinition = "TEXT")
    private String caption; // Mô tả / caption video

    @ManyToOne
    @JoinColumn(name = "uploader_id")
    private User uploader; // Người đăng video

    private LocalDateTime createdAt = LocalDateTime.now();

    // Người đã tim video này
    @ManyToMany
    @JoinTable(
        name = "pesoctok_likes",
        joinColumns = @JoinColumn(name = "video_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> likedUsers = new HashSet<>();
}
