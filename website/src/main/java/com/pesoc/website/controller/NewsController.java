package com.pesoc.website.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import com.pesoc.website.model.Article;
import com.pesoc.website.model.Comment;
import com.pesoc.website.model.Tag;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.ArticleRepository;
import com.pesoc.website.repository.CommentRepository;
import com.pesoc.website.repository.TagRepository;
import com.pesoc.website.repository.UserRepository;
import com.pesoc.website.service.FirebaseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Controller
public class NewsController {
    @Autowired private ArticleRepository articleRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private FirebaseService firebaseService;
    @Autowired private UserRepository userRepository;
    @Autowired private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Autowired private com.pesoc.website.repository.InAppNotificationRepository inAppNotificationRepository;

    // Trang danh sách tin tức
    @GetMapping("/news")
    public String news(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 15);
        Page<Article> articlePage = articleRepository.findAllByOrderByCreatedAtDesc(pageable);
        model.addAttribute("articlePage", articlePage);
        return "news";
    }

    // Trang chi tiết bài viết
    @GetMapping("/news/{id}")
    public String articleDetail(@PathVariable Long id, Model model) {
        Article article = articleRepository.findById(id).orElse(null);
        if (article == null) return "redirect:/news";
        model.addAttribute("article", article);
        model.addAttribute("latestArticles", articleRepository.findTop5ByIdNotOrderByCreatedAtDesc(id));
        return "news-detail";
    }

    @PostMapping("/news/{id}/comment")
    public String postComment(@PathVariable Long id, 
                              @RequestParam String content, 
                              @RequestParam(required = false) Long parentId, 
                              HttpSession session, 
                              jakarta.servlet.http.HttpServletRequest request) {
        
        User loggedInUser = (User) session.getAttribute("logInUser");
        if (loggedInUser == null) return "redirect:/login"; 
        
        Article article = articleRepository.findById(id).orElse(null);
        if (article != null && !content.trim().isEmpty()) {
            Comment comment = new Comment();
            comment.setContent(content);
            comment.setUser(loggedInUser);
            comment.setArticle(article);
            comment.setCreatedAt(java.time.LocalDateTime.now());
            
            // Xử lý gắn cha - con cho comment
            Comment parent = null;
            if (parentId != null) {
                parent = commentRepository.findById(parentId).orElse(null);
                if (parent != null) {
                    comment.setParentComment(parent);
                }
            }
            
            commentRepository.save(comment);
            
            // --- XÓA CÁI REGEX CŨ ĐI VÀ THAY BẰNG ĐOẠN NÀY ---
            String authorUsername = (article.getAuthor() != null) ? article.getAuthor().getUsername() : null;
            boolean isTagAll = false;
            java.util.Set<String> mentionedUsernames = new java.util.HashSet<>();

            try {
                // 🌟 THẦN CHÚ MỚI: Quét tất cả User trong DB để tìm tên chính xác 100% (hỗ trợ dấu cách, Tiếng Việt)
                List<User> allUsers = userRepository.findAll(); 
                for (User u : allUsers) {
                    if (content.contains("@" + u.getUsername())) {
                        mentionedUsernames.add(u.getUsername());
                    }
                }
                
                if (content.contains("@all")) {
                    isTagAll = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // ------------------------------------------------

            // --- XỬ LÝ PHÂN LUỒNG BẮN THÔNG BÁO TỐI ƯU NHẤT ---
            try {
                String currentLoginUser = loggedInUser.getUsername();
                String targetUrl = "/news/" + id;
                
                // 🛡️ BỘ LỌC CHỐNG TRÙNG LẶP (Ai nhận thông báo rồi thì sẽ vào danh sách này)
                java.util.Set<String> alreadyNotified = new java.util.HashSet<>();
                alreadyNotified.add(currentLoginUser); // Không bao giờ tự gửi cho chính mình

                // 1. NẾU LÀ BÌNH LUẬN TRẢ LỜI (REPLY)
                if (parent != null && parent.getUser() != null) {
                    String parentAuthor = parent.getUser().getUsername();
                    if (!alreadyNotified.contains(parentAuthor)) {
                        String replyTitle = "Phản hồi mới! 💬";
                        String replyBody = currentLoginUser + " đã trả lời bình luận của bạn: \"" + content + "\"";
                        
                        sendInAppNotification(parentAuthor, replyTitle, replyBody, targetUrl);
                        firebaseService.sendToUser(parentAuthor, replyTitle, replyBody, targetUrl);
                        
                        alreadyNotified.add(parentAuthor); // Đã gửi -> Đưa vào danh sách đen
                    }
                } 
                // 2. NẾU LÀ BÌNH LUẬN GỐC (COMMENT VÀO BÀI VIẾT)
                else if (authorUsername != null) {
                    if (!alreadyNotified.contains(authorUsername)) {
                        String commentTitle = "Bình luận mới! 💬";
                        String commentBody = currentLoginUser + " vừa bình luận vào bài viết của bạn: \"" + content + "\"";
                        
                        sendInAppNotification(authorUsername, commentTitle, commentBody, targetUrl);
                        firebaseService.sendToUser(authorUsername, commentTitle, commentBody, targetUrl);
                        
                        alreadyNotified.add(authorUsername); // Đã gửi -> Đưa vào danh sách đen
                    }
                }

                // 3. XỬ LÝ TAG TÊN (@all hoặc tag đích danh)
                if (isTagAll) {
                    // a) Bắn Web Push (Firebase) cho tất cả
                    firebaseService.sendToAllUsers(
                        currentLoginUser,
                        "Thông báo diện rộng! 📢",
                        currentLoginUser + " vừa nhắc đến tất cả mọi người trong một bình luận: \"" + content + "\"", 
                        targetUrl
                    );

                    // b) Bắn In-App (Cái chuông) cho tất cả (trừ những người đã nhận ở bước 1 & 2)
                    List<User> allUsers = userRepository.findAll();
                    for (User u : allUsers) {
                        String receiver = u.getUsername();
                        if (!alreadyNotified.contains(receiver)) {
                            sendInAppNotification(receiver, "Thông báo diện rộng! 📢", currentLoginUser + " vừa nhắc đến tất cả mọi người trong một bình luận: \"" + content + "\"", targetUrl);
                            alreadyNotified.add(receiver); // Đánh dấu là đã gửi
                        }
                    }
                } else {
                    // Chỉ tag lẻ tẻ vài người
                    for (String receiver : mentionedUsernames) {
                        if (!alreadyNotified.contains(receiver)) {
                            String tagTitle = "Bạn được nhắc tên! 🏷️";
                            String tagBody = currentLoginUser + " vừa nhắc đến bạn trong một bình luận: \"" + content + "\"";
                            
                            sendInAppNotification(receiver, tagTitle, tagBody, targetUrl);
                            firebaseService.sendToUser(receiver, tagTitle, tagBody, targetUrl);
                            
                            alreadyNotified.add(receiver); // Đánh dấu là đã gửi
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        String referer = request.getHeader("Referer");
        return "redirect:" + referer;
    }

    @PostMapping("/admin/news/{id}/delete")
    public String deleteArticle(@PathVariable Long id) {
        Article article = articleRepository.findById(id).orElse(null);
        if(article != null){
            articleRepository.delete(article);
        }
        
        return "redirect:/news";
    }
    

    // Trong NewsController.java, thêm logic xử lý tag khi lưu bài viết
    private void processTags(Article article, String tagsInput) {
        if (tagsInput == null || tagsInput.isEmpty()) return;
        
        String[] tagNames = tagsInput.split(",");
        for (String name : tagNames) {
            String cleanName = name.trim().toLowerCase().replaceAll("#", "");
            if (!cleanName.isEmpty()) {
                Tag tag = tagRepository.findByName(cleanName);
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(cleanName);
                    tagRepository.save(tag);
                }
                article.getTags().add(tag);
            }
        }
    }

    // Route để lọc bài viết theo Hashtag
    @GetMapping("/news/tag/{tagName}")
    public String listByTag(@PathVariable String tagName, @RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 15);
        model.addAttribute("articlePage", articleRepository.findByTags_Name(tagName.toLowerCase(), pageable));
        model.addAttribute("currentTag", tagName);
        return "news";
    }

    // 1. Mở trang tạo bài viết
    @GetMapping("/news/create")
    public String createNewsPage(HttpSession session) {
        // Kiểm tra xem đã đăng nhập chưa mới cho viết bài
        if (session.getAttribute("logInUser") == null) return "redirect:/news";
        return "news-create";
    }

    // 2. Xử lý lưu bài viết mới
    @PostMapping("/news/create")
    public String saveArticle(
            @RequestParam("title") String title,
            @RequestParam("summary") String summary,
            @RequestParam("content") String content,
            @RequestParam("tagsInput") String tagsInput,
            @RequestParam(value = "thumbnailFile", required = false) MultipartFile thumbnailFile, 
            HttpSession session) {
        
        User loggedInUser = (User) session.getAttribute("logInUser");
        if (loggedInUser == null) return "redirect:/news";

        Article article = new Article();
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        article.setAuthor(loggedInUser);
        article.setCreatedAt(java.time.LocalDateTime.now());

        // XỬ LÝ LƯU ẢNH BÌA
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            try {
                String uploadDir = "uploads/news/";
                java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadDir);
                if (!java.nio.file.Files.exists(uploadPath)) {
                    java.nio.file.Files.createDirectories(uploadPath);
                }

                // Tạo tên file duy nhất bằng UUID
                String filename = java.util.UUID.randomUUID().toString() + "_" + thumbnailFile.getOriginalFilename();
                java.nio.file.Path filePath = uploadPath.resolve(filename);
                
                // Lưu file vật lý vào thư mục uploads/news/
                java.nio.file.Files.copy(thumbnailFile.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // ==========================================
                // DÒNG QUAN TRỌNG NHẤT: Lưu tên file vào DB
                // ==========================================
                article.setThumbnail(filename); 
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Xử lý Hashtag (Mapping Tag vào Article)
        processTags(article, tagsInput);

        // Lưu toàn bộ Article (bao gồm cả thumbnail đã set) vào DB
        articleRepository.save(article);

        // --- ĐOẠN CODE MỚI: XỬ LÝ 2 TRONG 1 ---
        // 1. Báo cho những người đang "bật chuông" theo dõi tác giả này
        firebaseService.sendToSubscribers(
            loggedInUser.getUsername(), 
            "PLAYER", 
            "Bài viết mới từ người bật thông báo! 📝", 
            loggedInUser.getUsername() + " vừa đăng bài viết mới: " + title, "/news/" + article.getId()
        );
        
        return "redirect:/news";
    }

    @PostMapping("/news/comment/{id}/like")
    @ResponseBody
    public Map<String, Object> toggleCommentLike(@PathVariable Long id, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        // 1. Kiểm tra đăng nhập
        User user = (User) session.getAttribute("logInUser");
        if (user == null) {
            response.put("success", false);
            response.put("message", "not_logged_in");
            return response;
        }

        // 2. Tìm comment trong DB
        Comment comment = commentRepository.findById(id).orElse(null);
        if (comment == null) {
            response.put("success", false);
            return response;
        }

        // 3. Xử lý logic Like / Bỏ Like
        boolean isLiked = comment.getLikedUsers().contains(user);
        if (isLiked) {
            // Đã like rồi thì bỏ like
            comment.getLikedUsers().remove(user);
            isLiked = false;
        } else {
            // Chưa like thì thêm like
            comment.getLikedUsers().add(user);
            isLiked = true;
        }
        
        // Lưu lại vào Database
        commentRepository.save(comment);

        // 4. Trả kết quả về cho giao diện (Frontend)
        response.put("success", true);
        response.put("isLiked", isLiked);
        response.put("newLikeCount", comment.getLikedUsers().size());
        
        return response;
    }

    // Hàm vừa lưu DB vừa bắn mạng nội bộ WebSocket Realtime
    private void sendInAppNotification(String receiver, String title, String body, String url) {
        try {
            // 1. Lưu lịch sử vào Database để xem lại sau
            com.pesoc.website.model.InAppNotification notif = new com.pesoc.website.model.InAppNotification();
            notif.setTitle(title);
            notif.setBody(body);
            notif.setUrl(url);
            notif.setReceiverUsername(receiver);
            inAppNotificationRepository.save(notif);

            // 2. Bắn WebSocket tới kênh riêng của người nhận (nếu họ đang online)
            messagingTemplate.convertAndSend("/topic/notifications/" + receiver, notif);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}