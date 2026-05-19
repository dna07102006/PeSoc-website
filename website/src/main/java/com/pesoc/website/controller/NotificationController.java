package com.pesoc.website.controller;

import com.pesoc.website.model.DeviceToken;
import com.pesoc.website.model.Subscription;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.DeviceTokenRepository;
import com.pesoc.website.repository.SubscriptionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired
    private DeviceTokenRepository tokenRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @PostMapping("/save-token")
    public ResponseEntity<?> saveToken(@RequestBody Map<String, String> payload, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) {
            return ResponseEntity.status(401).body("Chưa đăng nhập.");
        }

        String token = payload.get("token");
        
        // Tìm xem Token này đã từng có ai đăng ký trên máy này chưa
        var existingTokenOpt = tokenRepository.findByToken(token);
        
        if (existingTokenOpt.isPresent()) {
            // NẾU CÓ RỒI: Cập nhật chủ sở hữu mới (Sang tên từ User cũ sang User mới)
            DeviceToken deviceToken = existingTokenOpt.get();
            deviceToken.setUser(logInUser); // Đổi chủ!
            tokenRepository.save(deviceToken);
        } else {
            // NẾU CHƯA CÓ: Tạo mới tinh như bình thường
            DeviceToken deviceToken = new DeviceToken();
            deviceToken.setToken(token);
            deviceToken.setUser(logInUser);
            tokenRepository.save(deviceToken);
        }
        
        return ResponseEntity.ok("Cập nhật Token thiết bị thành công!");
    }

    @PostMapping("/delete-token")
    public ResponseEntity<?> deleteToken(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token != null && !token.trim().isEmpty()) {
            tokenRepository.deleteByToken(token);
            System.out.println("👉 Đã xoá Token thiết bị thành công khi user bấm Logout!");
        }
        return ResponseEntity.ok("Đã xoá token!");
    }

    @PostMapping("/toggle-bell")
    public ResponseEntity<?> toggleBell(@RequestParam String targetId, @RequestParam String targetType, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) {
            // Trả về một JSON Object chứa thông điệp lỗi rõ ràng
            return ResponseEntity.status(401).body(Map.of("status", "error", "message", "Cần đăng nhập để bật chuông sếp ơi!"));
        }

        var existingSub = subscriptionRepository.findByUserIdAndTargetIdAndTargetType(logInUser.getId(), targetId, targetType);

        if (existingSub.isPresent()) {
            // Nếu có rồi -> Xóa đi (Tắt chuông)
            subscriptionRepository.delete(existingSub.get());
            return ResponseEntity.ok(Map.of("status", "unsubscribed", "message", "Đã tắt chuông!"));
        } else {
            // Nếu chưa có -> Lưu mới (Bật chuông)
            Subscription sub = new Subscription();
            sub.setUser(logInUser);
            sub.setTargetId(targetId);
            sub.setTargetType(targetType);
            subscriptionRepository.save(sub);
            return ResponseEntity.ok(Map.of("status", "subscribed", "message", "Đã bật chuông thông báo!"));
        }
    }

    @Autowired
    private com.pesoc.website.repository.InAppNotificationRepository inAppNotificationRepository;

    // API 1: Lấy danh sách thông báo và số lượng chưa đọc lúc load trang
    @GetMapping("/inbox")
    public ResponseEntity<?> getMyInbox(HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body("Chưa đăng nhập");

        var list = inAppNotificationRepository.findTop15ByReceiverUsernameOrderByCreatedAtDesc(logInUser.getUsername());
        long unreadCount = inAppNotificationRepository.countByReceiverUsernameAndIsReadFalse(logInUser.getUsername());

        return ResponseEntity.ok(Map.of("notifications", list, "unreadCount", unreadCount));
    }

    // API 2: Bấm vào thông báo nào thì đánh dấu thông báo đó đã đọc
    @PostMapping("/mark-read/{id}")
    public ResponseEntity<?> markAsRead(@PathVariable Long id, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body("Chưa đăng nhập");

        inAppNotificationRepository.findById(id).ifPresent(notif -> {
            if (notif.getReceiverUsername().equals(logInUser.getUsername())) {
                notif.setRead(true);
                inAppNotificationRepository.save(notif);
            }
        });
        return ResponseEntity.ok("Đã đọc!");
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body("Chưa đăng nhập");

        // Tìm tất cả thông báo chưa đọc và chuyển thành đã đọc
        var unreadList = inAppNotificationRepository.findByReceiverUsernameAndIsReadFalse(logInUser.getUsername());
        for (var notif : unreadList) {
            notif.setRead(true);
        }
        inAppNotificationRepository.saveAll(unreadList);
        
        return ResponseEntity.ok("Đã dọn dẹp chuông!");
    }
}