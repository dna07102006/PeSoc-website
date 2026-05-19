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
            
            // --- QUÉT CÁC TÊN BỊ TAG TRONG Ô NHẬP ĐỂ LÀM BỘ LỌC ---
            String authorUsername = (article.getAuthor() != null) ? article.getAuthor().getUsername() : null;
            boolean isTagAll = false;
            java.util.Set<String> mentionedUsernames = new java.util.HashSet<>();

            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([a-zA-Z0-9_]+)");
                java.util.regex.Matcher matcher = pattern.matcher(content); 
                while (matcher.find()) {
                    mentionedUsernames.add(matcher.group(1)); 
                }
                isTagAll = mentionedUsernames.stream().anyMatch(name -> name.equalsIgnoreCase("all"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // --- XỬ LÝ PHÂN LUỒNG BẮN THÔNG BÁO THÔNG MINH ---
            try {
                if (isTagAll) {
                    // 1. Firebase (Web Push cũ)
                    firebaseService.sendToAllUsers(
                        loggedInUser.getUsername(),
                        "Thông báo diện rộng! 📢",
                        loggedInUser.getUsername() + " vừa nhắc đến tất cả mọi người trong một bình luận: \"" + content + "\"", "/news/" + id
                    );

                    // 2. 🌟 GIẢI PHÁP IN-APP CHO NHIỀU NGƯỜI: Vòng lặp gửi WebSocket cho tất cả
                    List<User> allUsers = userRepository.findAll(); // Lấy tất cả user từ DB
                    for (User u : allUsers) {
                        // Trừ bản thân người gõ comment ra, còn lại bắn hết!
                        if (!u.getUsername().equals(loggedInUser.getUsername())) {
                            sendInAppNotification(u.getUsername(), "Thông báo diện rộng! 📢", loggedInUser.getUsername() + " vừa nhắc đến tất cả mọi người trong một bình luận: \"" + content + "\"", "/news/" + id);
                        }
                    }
                } else {
                    if (parentId != null) {
                        // =========================================================
                        // TRƯỜNG HỢP 1: LÀ BÌNH LUẬN PHẢN HỒI (Tầng 2, Tầng 3...)
                        // =========================================================
                        
                        // BẢO HIỂM: Nếu sếp lỡ tay xóa chữ @tag trong ô nhập, hệ thống tự động lôi chủ nhân của comment cha ra để gửi.
                        if (mentionedUsernames.isEmpty() && parent != null && parent.getUser() != null) {
                            mentionedUsernames.add(parent.getUser().getUsername());
                        }

                        // Bắn thông báo "ĐÃ TRẢ LỜI" cho những người bị tag trong ô reply
                        for (String uname : mentionedUsernames) {
                            if (userRepository.findByUsername(uname) != null && !uname.equals(loggedInUser.getUsername())) {
                                firebaseService.sendToUser(
                                    uname, 
                                    "Phản hồi mới! 💬", 
                                    loggedInUser.getUsername() + " đã trả lời bình luận của bạn: \"" + content + "\"", "/news/" + id
                                );
                                sendInAppNotification(uname, "Phản hồi mới! 💬", loggedInUser.getUsername() + " đã trả lời bình luận của bạn", "/news/" + id);
                            }
                        }
                    } else {
                        // =========================================================
                        // TRƯỜNG HỢP 2: LÀ BÌNH LUẬN GỐC (Tầng 1)
                        // =========================================================
                        
                        // a) Bắn cho Tác giả bài viết trước
                        if (authorUsername != null && !authorUsername.equals(loggedInUser.getUsername())) {
                            mentionedUsernames.remove(authorUsername); // Xóa khỏi danh sách tag để tránh bị bắn chuông 2 lần
                            firebaseService.sendToUser(
                                authorUsername, 
                                "Bình luận mới! 💬", 
                                loggedInUser.getUsername() + " vừa bình luận vào bài viết của bạn: \"" + content + "\"", "/news/" + id
                            );
                            sendInAppNotification(authorUsername, "Bình luận mới! 💬", loggedInUser.getUsername() + " vừa bình luận vào bài viết của bạn: \"" + content + "\"", "/news/" + id);
                        }
                        
                        // b) Bắn cho những người bị tag lẻ tẻ trong bình luận gốc (Nhắc tên)
                        for (String uname : mentionedUsernames) {
                            if (userRepository.findByUsername(uname) != null && !uname.equals(loggedInUser.getUsername())) {
                                firebaseService.sendToUser(
                                    uname, 
                                    "Bạn được nhắc tên! 🏷️", 
                                    loggedInUser.getUsername() + " vừa nhắc đến bạn trong một bình luận: \"" + content + "\"", "/news/" + id
                                );
                                sendInAppNotification(uname, "Bạn được nhắc tên! 🏷️", loggedInUser.getUsername() + " vừa nhắc đến bạn trong một bình luận: \"" + content + "\"", "/news/" + id);
                            }
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