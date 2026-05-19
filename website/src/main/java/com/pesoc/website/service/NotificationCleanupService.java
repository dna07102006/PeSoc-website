package com.pesoc.website.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.pesoc.website.repository.InAppNotificationRepository;
import com.pesoc.website.repository.UserRepository;
import com.pesoc.website.model.User;
import com.pesoc.website.model.InAppNotification;
import java.util.List;

@Service
public class NotificationCleanupService {

    @Autowired private InAppNotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    // Chạy lúc 3h sáng mỗi ngày
    @Scheduled(cron = "0 0 3 * * ?")
    public void keepOnlyTop15Notifications() {
        try {
            List<User> allUsers = userRepository.findAll();
            int totalDeleted = 0;

            for (User u : allUsers) {
                // Lấy tất cả thông báo của người này (đã sắp xếp mới nhất lên đầu)
                List<InAppNotification> notifs = notificationRepository.findByReceiverUsernameOrderByCreatedAtDesc(u.getUsername());
                
                // Nếu nhiều hơn 15 cái, thì cắt bỏ từ cái thứ 16 trở đi đem đi xóa
                if (notifs.size() > 15) {
                    List<InAppNotification> rácCầnXóa = notifs.subList(15, notifs.size());
                    notificationRepository.deleteAll(rácCầnXóa);
                    totalDeleted += rácCầnXóa.size();
                }
            }
            
            System.out.println("🧹 [DỌN DẸP] Đã xóa " + totalDeleted + " thông báo thừa, giữ lại đúng 15 cái/người!");
        } catch (Exception e) {
            System.err.println("❌ [LỖI] Dọn dẹp thất bại: " + e.getMessage());
        }
    }
}