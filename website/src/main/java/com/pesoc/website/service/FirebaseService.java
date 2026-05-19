package com.pesoc.website.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.pesoc.website.model.DeviceToken;
import org.springframework.beans.factory.annotation.Autowired;
import com.pesoc.website.repository.DeviceTokenRepository;
import com.pesoc.website.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;

@Service
public class FirebaseService {
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private DeviceTokenRepository tokenRepository;
    
    // Hàm này tự động chạy 1 lần duy nhất khi sếp bật Server Java
    @PostConstruct
    public void initialize() {
        try {
            // Đọc file chìa khóa sếp vừa ném vào thư mục resources
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase-key.json");

            if (serviceAccount == null) {
                System.err.println("Lỗi: Không tìm thấy file firebase-key.json!");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase Admin SDK đã khởi động thành công!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Hàm thực thi việc nhồi nội dung và bắn thông báo đi
    public void sendNotification(String targetToken, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(targetToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            // Gửi đi
            String response = FirebaseMessaging.getInstance().send(message);
            System.out.println("Đã bắn thông báo thành công! Mã phản hồi: " + response);
        } catch (Exception e) {
            System.err.println("Lỗi khi bắn thông báo: " + e.getMessage());
        }
    }

    // BẢN NÂNG CẤP: Gửi hàng loạt + Tự động dọn dẹp Token chết từ Google
    public void sendNotificationToMultiple(List<String> tokens, String title, String body, String url) {
        if (tokens == null || tokens.isEmpty()) return;
        
        String finalUrl = (url == null || url.trim().isEmpty()) ? "/home" : url;

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setFcmOptions(WebpushFcmOptions.builder().setLink(finalUrl).build())
                            .build())
                    .putData("url", finalUrl)
                    .build();

            // Thực thi gửi hàng loạt và nhận về danh sách kết quả chi tiết từng token
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            System.out.println("Đã bắn thành công cho " + response.getSuccessCount() + " thiết bị.");

            // 🌟 ĐOẠN CODE TỰ ĐỘNG THU GOM RÁC CỦA GOOGLE:
            if (response.getFailureCount() > 0) {
                var responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        var exception = responses.get(i).getException();
                        
                        // Nếu Google báo lỗi là Token này không tồn tại hoặc đã bị hủy (UNREGISTERED)
                        if (exception != null && 
                            (exception.getMessagingErrorCode() == com.google.firebase.messaging.MessagingErrorCode.UNREGISTERED || 
                             exception.getMessagingErrorCode() == com.google.firebase.messaging.MessagingErrorCode.INVALID_ARGUMENT)) {
                            
                            String deadToken = tokens.get(i); // Tìm xem token vị trí thứ mấy bị chết
                            tokenRepository.deleteByToken(deadToken); // Tiễn cụ đi chân lạnh toát khỏi DB mình luôn!
                            System.out.println("❌ Hệ thống tự động quét và xóa Token chết khỏi DB: " + deadToken);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi tổng luồng bắn thông báo hàng loạt: " + e.getMessage());
        }
    }

    // HÀM MỚI: Bắn theo chuông có truyền URL
    public void sendToSubscribers(String targetId, String targetType, String title, String body, String url) {
        List<Long> subscribedUserIds = subscriptionRepository.findByTargetIdAndTargetType(targetId, targetType).stream()
                .filter(sub -> sub.getUser() != null).map(sub -> sub.getUser().getId()).toList();
        if (subscribedUserIds.isEmpty()) return;

        List<String> targetTokens = tokenRepository.findAll().stream()
                .filter(dt -> dt.getUser() != null && subscribedUserIds.contains(dt.getUser().getId()))
                .map(DeviceToken::getToken).toList();

        this.sendNotificationToMultiple(targetTokens, title, body, url); // Gọi hàm ở trên
    }

    // HÀM MỚI: Bắn đích danh 1 user có truyền URL
    public void sendToUser(String username, String title, String body, String url) {
        if (username == null || username.trim().isEmpty()) return;
        List<String> targetTokens = tokenRepository.findAll().stream()
                .filter(dt -> dt.getUser() != null && username.equalsIgnoreCase(dt.getUser().getUsername()))
                .map(DeviceToken::getToken).toList();
        if (targetTokens.isEmpty()) return;
        
        this.sendNotificationToMultiple(targetTokens, title, body, url);
    }

    // HÀM BỔ SUNG: Bắn thông báo cho TẤT CẢ MỌI NGƯỜI (Dùng cho @all)
    public void sendToAllUsers(String excludedUsername, String title, String body, String url) {
        // Quét lấy tất cả token trong DB, ngoại trừ token của cái ông vừa gõ comment
        List<String> allTokens = tokenRepository.findAll().stream()
                .filter(dt -> dt.getUser() != null && !dt.getUser().getUsername().equalsIgnoreCase(excludedUsername))
                .map(DeviceToken::getToken)
                .toList();

        if (allTokens.isEmpty()) return;

        // Bắn hàng loạt
        this.sendNotificationToMultiple(allTokens, title, body, url);
        System.out.println("📢 Đã kích hoạt lệnh gọi @all tới " + allTokens.size() + " thiết bị!");
    }

    // HÀM MỚI: Bắn cho fan của CẢ 2 CẦU THỦ nhưng tự động LỌC TRÙNG NGƯỜI NHẬN
    public void sendToMatchPlayersSubscribers(String p1Name, String p2Name, String title, String body, String url) {
        // 1. Lấy danh sách ID người theo dõi Player 1
        List<Long> subs1 = subscriptionRepository.findByTargetIdAndTargetType(p1Name, "PLAYER").stream()
                .filter(sub -> sub.getUser() != null).map(sub -> sub.getUser().getId()).toList();

        // 2. Lấy danh sách ID người theo dõi Player 2
        List<Long> subs2 = subscriptionRepository.findByTargetIdAndTargetType(p2Name, "PLAYER").stream()
                .filter(sub -> sub.getUser() != null).map(sub -> sub.getUser().getId()).toList();

        // 3. Sử dụng HashSet để TỰ ĐỘNG GỘP VÀ LOẠI BỎ các ID bị trùng nhau
        java.util.Set<Long> uniqueUserIds = new java.util.HashSet<>();
        uniqueUserIds.addAll(subs1);
        uniqueUserIds.addAll(subs2);

        // Không có ai theo dõi cả 2 ông thì thôi, nghỉ khỏe
        if (uniqueUserIds.isEmpty()) return;

        // 4. Gom toàn bộ Token thiết bị của danh sách ID đã lọc trùng ở trên
        List<String> targetTokens = tokenRepository.findAll().stream()
                .filter(dt -> dt.getUser() != null && uniqueUserIds.contains(dt.getUser().getId()))
                .map(DeviceToken::getToken).toList();

        // 5. Phát loa 1 phát duy nhất cho Google Firebase xử lý
        this.sendNotificationToMultiple(targetTokens, title, body, url);
    }
}