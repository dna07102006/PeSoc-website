package com.pesoc.website.controller;

import com.pesoc.website.model.*;
import com.pesoc.website.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;

@Controller
public class PesocTokController {

    @Autowired private PesocTokVideoRepository videoRepo;
    @Autowired private PesocTokCommentRepository commentRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private InAppNotificationRepository notifRepo;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private SubscriptionRepository subscriptionRepo;
    @Autowired private com.pesoc.website.service.FirebaseService firebaseService;

    // ============================================================
    // TRANG CHÍNH PESOCTOK
    // ============================================================
    @GetMapping("/pesoctok")
    public String pesocTokPage() {
        return "pesoctok";
    }

    // ============================================================
    // API: LẤY DANH SÁCH VIDEO (JSON)
    // ============================================================
    @GetMapping("/api/pesoctok/videos")
    @ResponseBody
    public ResponseEntity<?> getVideos(HttpSession session) {
        List<PesocTokVideo> videos = videoRepo.findAllByOrderByCreatedAtDesc();
        User logInUser = (User) session.getAttribute("logInUser");

        List<Map<String, Object>> result = new ArrayList<>();
        for (PesocTokVideo v : videos) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", v.getId());
            map.put("youtubeId", v.getYoutubeId());
            map.put("caption", v.getCaption());
            map.put("createdAt", v.getCreatedAt());
            map.put("likeCount", v.getLikedUsers().size());
            map.put("commentCount", commentRepo.countByVideo(v));

            // Thông tin uploader
            Map<String, Object> uploader = new HashMap<>();
            uploader.put("username", v.getUploader().getUsername());
            uploader.put("avatar", v.getUploader().getAvatar());
            map.put("uploader", uploader);

            // Trạng thái đã tim chưa
            boolean liked = logInUser != null && v.getLikedUsers().stream()
                    .anyMatch(u -> u.getId().equals(logInUser.getId()));
            map.put("likedByMe", liked);

            // Quyền edit/xóa
            boolean canEdit = logInUser != null && (
                logInUser.getId().equals(v.getUploader().getId()) ||
                "ADMIN".equals(logInUser.getRole())
            );
            map.put("canEdit", canEdit);

            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // ============================================================
    // API: UPLOAD VIDEO MỚI
    // ============================================================
    @PostMapping("/api/pesoctok/upload")
    @ResponseBody
    public ResponseEntity<?> uploadVideo(@RequestBody Map<String, String> body, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        String youtubeUrl = body.get("youtubeUrl");
        String caption = body.getOrDefault("caption", "");

        if (youtubeUrl == null || youtubeUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL không được để trống!"));
        }

        String youtubeId = extractYoutubeId(youtubeUrl);
        if (youtubeId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Link YouTube không hợp lệ! Hãy dùng link youtube.com hoặc youtu.be"));
        }

        PesocTokVideo video = new PesocTokVideo();
        video.setYoutubeUrl(youtubeUrl);
        video.setYoutubeId(youtubeId);
        video.setCaption(caption);
        video.setUploader(logInUser);
        video.setCreatedAt(LocalDateTime.now());
        videoRepo.save(video);

        // Gửi thông báo cho tất cả người theo dõi (bấm chuông ở profile)
        List<Subscription> followers = subscriptionRepo.findByTargetIdAndTargetType(
                logInUser.getUsername(), "PLAYER");
        String notifTitle = logInUser.getUsername() + " vừa đăng video mới trên PeSocTok 🎬";
        String notifBody  = truncate(caption != null && !caption.isBlank() ? caption : "Video mới", 80);
        String notifUrl   = "/pesoctok?v=" + video.getId();
        for (Subscription sub : followers) {
            String followerUsername = sub.getUser().getUsername();
            if (!followerUsername.equals(logInUser.getUsername())) {
                sendNotif(followerUsername, notifTitle, notifBody, notifUrl);
            }
        }
        // WebPush: gửi thông báo đẩy đến thiết bị của người theo dõi
        firebaseService.sendToSubscribers(logInUser.getUsername(), "PLAYER", notifTitle, notifBody, notifUrl);

        // Xử lý @mention trong caption (in-app + WebPush)
        handleCaptionMentions(caption, logInUser, video);

        return ResponseEntity.ok(Map.of("message", "Đăng video thành công!", "id", video.getId()));
    }

    // ============================================================
    // API: TOGGLE LIKE VIDEO
    // ============================================================
    @PostMapping("/api/pesoctok/like/{videoId}")
    @ResponseBody
    public ResponseEntity<?> toggleLike(@PathVariable Long videoId, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        Optional<PesocTokVideo> opt = videoRepo.findById(videoId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PesocTokVideo video = opt.get();
        boolean alreadyLiked = video.getLikedUsers().stream()
                .anyMatch(u -> u.getId().equals(logInUser.getId()));

        if (alreadyLiked) {
            video.getLikedUsers().removeIf(u -> u.getId().equals(logInUser.getId()));
        } else {
            video.getLikedUsers().add(logInUser);
            // Thông báo cho chủ video khi có người tim (không tự tim chính mình)
            String owner = video.getUploader().getUsername();
            if (!owner.equals(logInUser.getUsername())) {
                sendNotif(owner,
                    logInUser.getUsername() + " đã thích video của bạn ❤️",
                    truncate(video.getCaption() != null ? video.getCaption() : "Video của bạn", 80),
                    "/pesoctok?v=" + video.getId());
            }
        }
        videoRepo.save(video);

        return ResponseEntity.ok(Map.of(
            "liked", !alreadyLiked,
            "likeCount", video.getLikedUsers().size()
        ));
    }

    // ============================================================
    // API: XÓA VIDEO
    // ============================================================
    @DeleteMapping("/api/pesoctok/video/{videoId}")
    @ResponseBody
    public ResponseEntity<?> deleteVideo(@PathVariable Long videoId, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        Optional<PesocTokVideo> opt = videoRepo.findById(videoId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PesocTokVideo video = opt.get();
        boolean canDelete = logInUser.getId().equals(video.getUploader().getId()) ||
                            "ADMIN".equals(logInUser.getRole());
        if (!canDelete) return ResponseEntity.status(403).body(Map.of("error", "Bạn không có quyền xóa video này!"));

        videoRepo.delete(video);
        return ResponseEntity.ok(Map.of("message", "Đã xóa video!"));
    }

    // ============================================================
    // API: SỬA CAPTION VIDEO
    // ============================================================
    @PutMapping("/api/pesoctok/video/{videoId}/caption")
    @ResponseBody
    public ResponseEntity<?> editCaption(@PathVariable Long videoId, @RequestBody Map<String, String> body, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        Optional<PesocTokVideo> opt = videoRepo.findById(videoId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PesocTokVideo video = opt.get();
        boolean canEdit = logInUser.getId().equals(video.getUploader().getId()) ||
                          "ADMIN".equals(logInUser.getRole());
        if (!canEdit) return ResponseEntity.status(403).body(Map.of("error", "Bạn không có quyền sửa video này!"));

        String newCaption = body.getOrDefault("caption", "");
        video.setCaption(newCaption);
        videoRepo.save(video);

        // Xử lý @mention trong caption mới (in-app + WebPush)
        handleCaptionMentions(newCaption, logInUser, video);

        return ResponseEntity.ok(Map.of("message", "Đã cập nhật caption!"));
    }

    // ============================================================
    // API: LẤY BÌNH LUẬN CỦA VIDEO
    // ============================================================
    @GetMapping("/api/pesoctok/comments/{videoId}")
    @ResponseBody
    public ResponseEntity<?> getComments(@PathVariable Long videoId, HttpSession session) {
        Optional<PesocTokVideo> opt = videoRepo.findById(videoId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        User logInUser = (User) session.getAttribute("logInUser");
        List<PesocTokComment> comments = commentRepo.findByVideoAndParentCommentIsNullOrderByCreatedAtDesc(opt.get());

        return ResponseEntity.ok(comments.stream().map(c -> buildCommentMap(c, logInUser, 0)).toList());
    }

    // ============================================================
    // API: ĐĂNG BÌNH LUẬN / REPLY
    // ============================================================
    @PostMapping("/api/pesoctok/comment/{videoId}")
    @ResponseBody
    public ResponseEntity<?> postComment(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> body,
            HttpSession session) {

        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        Optional<PesocTokVideo> videoOpt = videoRepo.findById(videoId);
        if (videoOpt.isEmpty()) return ResponseEntity.notFound().build();

        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bình luận không được rỗng!"));
        }

        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;

        PesocTokComment comment = new PesocTokComment();
        comment.setContent(content);
        comment.setUser(logInUser);
        comment.setVideo(videoOpt.get());
        comment.setCreatedAt(LocalDateTime.now());

        if (parentId != null) {
            commentRepo.findById(parentId).ifPresent(comment::setParentComment);
        }

        commentRepo.save(comment);

        // Thông báo cho chủ video khi có người bình luận
        String videoOwner = videoOpt.get().getUploader().getUsername();
        if (!videoOwner.equals(logInUser.getUsername())) {
            sendNotif(videoOwner,
                logInUser.getUsername() + " đã bình luận video của bạn 💬",
                truncate(content, 80),
                "/pesoctok?v=" + videoId + "&c=" + comment.getId());
        }

        // Xử lý @mention - gửi thông báo cho user được tag
        handleMentions(content, logInUser, videoOpt.get(), comment.getId());

        long commentCount = commentRepo.countByVideo(videoOpt.get());
        return ResponseEntity.ok(Map.of(
            "comment", buildCommentMap(comment, logInUser, 0),
            "commentCount", commentCount
        ));
    }

    // ============================================================
    // API: TOGGLE LIKE BÌNH LUẬN
    // ============================================================
    @PostMapping("/api/pesoctok/comment/{commentId}/like")
    @ResponseBody
    public ResponseEntity<?> toggleCommentLike(@PathVariable Long commentId, HttpSession session) {
        User logInUser = (User) session.getAttribute("logInUser");
        if (logInUser == null) return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập!"));

        Optional<PesocTokComment> opt = commentRepo.findById(commentId);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        PesocTokComment comment = opt.get();
        boolean alreadyLiked = comment.getLikedUsers().stream()
                .anyMatch(u -> u.getId().equals(logInUser.getId()));

        if (alreadyLiked) {
            comment.getLikedUsers().removeIf(u -> u.getId().equals(logInUser.getId()));
        } else {
            comment.getLikedUsers().add(logInUser);
            // Thông báo cho chủ bình luận khi có người tim
            String commentOwner = comment.getUser().getUsername();
            if (!commentOwner.equals(logInUser.getUsername())) {
                Long vidId = comment.getVideo().getId();
                sendNotif(commentOwner,
                    logInUser.getUsername() + " đã thích bình luận của bạn ❤️",
                    truncate(comment.getContent(), 80),
                    "/pesoctok?v=" + vidId + "&c=" + commentId);
            }
        }
        commentRepo.save(comment);

        return ResponseEntity.ok(Map.of(
            "liked", !alreadyLiked,
            "likeCount", comment.getLikedUsers().size()
        ));
    }

    // ============================================================
    // API: TÌM KIẾM USER ĐỂ @MENTION
    // ============================================================
    @GetMapping("/api/pesoctok/mention-users")
    @ResponseBody
    public ResponseEntity<?> mentionUsers(@RequestParam(required = false, defaultValue = "") String q) {
        List<User> users = (q == null || q.isBlank())
            ? userRepo.findAllByOrderByUsernameAsc()   // @ không có query → trả về tất cả theo alphabet
            : userRepo.searchUsers(q.toLowerCase());   // có query → lọc theo tên
        List<Map<String, Object>> result = users.stream().limit(20).map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("username", u.getUsername());
            m.put("avatar", u.getAvatar());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    // ============================================================
    // HELPER: Trích xuất YouTube ID từ nhiều dạng URL
    // ============================================================
    private String extractYoutubeId(String url) {
        if (url == null || url.isBlank()) return null;

        // youtu.be/ID
        Pattern p1 = Pattern.compile("youtu\\.be/([\\w-]{11})");
        Matcher m1 = p1.matcher(url);
        if (m1.find()) return m1.group(1);

        // youtube.com/watch?v=ID
        Pattern p2 = Pattern.compile("[?&]v=([\\w-]{11})");
        Matcher m2 = p2.matcher(url);
        if (m2.find()) return m2.group(1);

        // youtube.com/shorts/ID
        Pattern p3 = Pattern.compile("youtube\\.com/shorts/([\\w-]{11})");
        Matcher m3 = p3.matcher(url);
        if (m3.find()) return m3.group(1);

        // youtube.com/embed/ID
        Pattern p4 = Pattern.compile("youtube\\.com/embed/([\\w-]{11})");
        Matcher m4 = p4.matcher(url);
        if (m4.find()) return m4.group(1);

        return null;
    }

    // ============================================================
    // HELPER: Xây dựng Map từ Comment để trả về JSON
    // ============================================================
    private Map<String, Object> buildCommentMap(PesocTokComment c, User logInUser, int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("content", c.getContent());
        map.put("createdAt", c.getCreatedAt());
        map.put("likeCount", c.getLikedUsers().size());

        boolean liked = logInUser != null && c.getLikedUsers().stream()
                .anyMatch(u -> u.getId().equals(logInUser.getId()));
        map.put("likedByMe", liked);

        Map<String, Object> user = new HashMap<>();
        user.put("username", c.getUser().getUsername());
        user.put("avatar", c.getUser().getAvatar());
        map.put("user", user);

        // Tải reply nếu chưa quá sâu (tối đa 3 cấp)
        if (depth < 3 && c.getReplies() != null && !c.getReplies().isEmpty()) {
            map.put("replies", c.getReplies().stream()
                    .sorted(Comparator.comparing(PesocTokComment::getCreatedAt))
                    .map(r -> buildCommentMap(r, logInUser, depth + 1))
                    .toList());
        } else {
            map.put("replies", List.of());
        }

        return map;
    }

    // ============================================================
    // HELPER: Trích xuất tất cả username được @mention từ text
    // Hỗ trợ 3 format: @username​ (ZWSP) | @[username] | @word
    // ============================================================
    private Set<String> extractMentions(String text) {
        Set<String> usernames = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return usernames;

        // Format 1: @username + U+200B (zero-width space terminator)
        Matcher m1 = Pattern.compile("@([^​@\n]+)​").matcher(text);
        while (m1.find()) usernames.add(m1.group(1).trim());

        // Format 2: @[username] — backward compat
        Matcher m2 = Pattern.compile("@\\[([^\\]]+)\\]").matcher(text);
        while (m2.find()) usernames.add(m2.group(1).trim());

        // Format 3: @simpleword — chạy trên text sau khi đã xóa format 1 & 2
        String remaining = text
            .replaceAll("@[^​@\n]+​", "")
            .replaceAll("@\\[[^\\]]+\\]", "");
        Matcher m3 = Pattern.compile("@(\\w+)").matcher(remaining);
        while (m3.find()) usernames.add(m3.group(1));

        return usernames;
    }

    // ============================================================
    // HELPER: Gửi thông báo mention (dùng chung cho bình luận & caption)
    // ============================================================
    private void doSendMentionNotif(String username, String senderUsername,
                                     String title, String body, String url, Set<String> mentioned) {
        if (username == null || username.isEmpty()) return;
        if (mentioned.contains(username)) return;
        if (username.equalsIgnoreCase(senderUsername)) return;
        if (userRepo.findByUsername(username) == null) return;
        mentioned.add(username);
        sendNotif(username, title, body, url); // sendNotif đã bao gồm WebPush
    }

    // ============================================================
    // HELPER: Xử lý @mention trong BÌNH LUẬN (gồm cả @all)
    // ============================================================
    private void handleMentions(String content, User sender, PesocTokVideo video, Long commentId) {
        Set<String> mentioned = new HashSet<>();
        String url   = "/pesoctok?v=" + video.getId() + "&c=" + commentId;
        String title = sender.getUsername() + " đã nhắc đến bạn trong bình luận 📢";
        String body  = truncate(content, 80);

        // --- @all: thông báo tất cả người đã bình luận trên video này ---
        boolean hasAtAll = content.contains("@all​") || content.matches("(?s).*@all(\\s|$).*");
        if (hasAtAll) {
            commentRepo.findDistinctCommentersByVideo(video).forEach(commenter ->
                doSendMentionNotif(commenter.getUsername(), sender.getUsername(), title, body, url, mentioned));
        }

        // --- Mention cá nhân ---
        extractMentions(content).stream()
            .filter(u -> !u.equalsIgnoreCase("all"))
            .forEach(u -> doSendMentionNotif(u, sender.getUsername(), title, body, url, mentioned));
    }

    // ============================================================
    // HELPER: Xử lý @mention trong CAPTION video (upload & edit)
    // ============================================================
    private void handleCaptionMentions(String caption, User uploader, PesocTokVideo video) {
        if (caption == null || caption.isBlank()) return;
        Set<String> mentioned = new HashSet<>();
        String url   = "/pesoctok?v=" + video.getId();
        String title = uploader.getUsername() + " đã nhắc đến bạn trong video 📢";
        String body  = truncate(caption, 80);

        extractMentions(caption).stream()
            .filter(u -> !u.equalsIgnoreCase("all")) // @all không có nghĩa trong caption
            .forEach(u -> doSendMentionNotif(u, uploader.getUsername(), title, body, url, mentioned));
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    // ============================================================
    // HELPER: Tạo & gửi thông báo in-app + WebSocket + WebPush
    // ============================================================
    private void sendNotif(String receiverUsername, String title, String body, String url) {
        // 1. In-app notification (lưu DB + đẩy qua WebSocket)
        InAppNotification notif = new InAppNotification();
        notif.setTitle(title);
        notif.setBody(body);
        notif.setUrl(url);
        notif.setReceiverUsername(receiverUsername);
        notif.setCreatedAt(LocalDateTime.now());
        notifRepo.save(notif);
        messagingTemplate.convertAndSend("/topic/notifications/" + receiverUsername, notif);

        // 2. WebPush (đẩy đến thiết bị qua Firebase FCM)
        firebaseService.sendToUser(receiverUsername, title, body, url);
    }
}
