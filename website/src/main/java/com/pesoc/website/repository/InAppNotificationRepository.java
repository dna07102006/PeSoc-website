// --- Repository: InAppNotificationRepository.java ---
package com.pesoc.website.repository;

import com.pesoc.website.model.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    // Lấy 15 thông báo mới nhất của user
    List<InAppNotification> findTop15ByReceiverUsernameOrderByCreatedAtDesc(String username);
    // Đếm số thông báo chưa đọc để hiển thị số Badge đỏ
    long countByReceiverUsernameAndIsReadFalse(String username);

    // Tìm tất cả thông báo chưa đọc của user
    List<InAppNotification> findByReceiverUsernameAndIsReadFalse(String username);
}