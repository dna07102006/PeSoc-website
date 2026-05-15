package com.pesoc.website.repository;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.pesoc.website.model.EloHistory;
import com.pesoc.website.model.User;

@Repository
public interface EloHistoryRepository extends JpaRepository<EloHistory, Long> {
    // Dùng để lấy lịch sử Elo (có phân trang)
    Page<EloHistory> findByUser(User user, Pageable pageable);

    // Dùng để tìm mốc Elo NGAY TRƯỚC ĐÓ để tính ra được con số Biến động (+/-)
    EloHistory findFirstByUserAndChangeDateLessThanOrderByChangeDateDesc(User user, LocalDateTime date);
}
