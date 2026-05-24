package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
@Table(name = "pesoctok_comments")
public class PesocTokComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "video_id")
    private PesocTokVideo video;

    // Bình luận cha (null nếu là bình luận gốc)
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private PesocTokComment parentComment;

    // Các reply của bình luận này
    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL)
    private List<PesocTokComment> replies;

    // Người đã tim bình luận này
    @ManyToMany
    @JoinTable(
        name = "pesoctok_comment_likes",
        joinColumns = @JoinColumn(name = "comment_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> likedUsers = new HashSet<>();
}
