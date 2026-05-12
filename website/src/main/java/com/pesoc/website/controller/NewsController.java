package com.pesoc.website.controller;

import java.util.HashMap;
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
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


@Controller
public class NewsController {
    @Autowired private ArticleRepository articleRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private TagRepository tagRepository;

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

    // Xử lý gửi bình luận
    // Thay thế hàm postComment cũ bằng hàm này:
    @PostMapping("/news/{id}/comment")
    public String postComment(@PathVariable Long id, 
                              @RequestParam String content, 
                              @RequestParam(required = false) Long parentId, // Thêm tham số này
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
            
            // NẾU CÓ PARENT ID -> GÁN ĐÂY LÀ BÌNH LUẬN TRẢ LỜI
            if (parentId != null) {
                Comment parent = commentRepository.findById(parentId).orElse(null);
                if (parent != null) {
                    comment.setParentComment(parent);
                }
            }
            
            commentRepository.save(comment);
        }
        
        // Tải lại trang hiện tại
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
}