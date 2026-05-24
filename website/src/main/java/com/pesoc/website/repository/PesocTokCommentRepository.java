package com.pesoc.website.repository;

import com.pesoc.website.model.PesocTokComment;
import com.pesoc.website.model.PesocTokVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PesocTokCommentRepository extends JpaRepository<PesocTokComment, Long> {
    // Lấy tất cả bình luận gốc (không phải reply) của 1 video, mới nhất trước
    List<PesocTokComment> findByVideoAndParentCommentIsNullOrderByCreatedAtDesc(PesocTokVideo video);

    // Đếm tổng số bình luận (gốc + reply) của 1 video
    long countByVideo(PesocTokVideo video);
}
