package com.pesoc.website.controller;

import com.pesoc.website.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.pesoc.website.model.Article;
import com.pesoc.website.repository.ArticleRepository;
import java.nio.file.*;
import java.util.*;


@RestController
@RequestMapping("/api")
public class ApiController {
    @Autowired private UserRepository userRepository;
    @Autowired private ArticleRepository articleRepository;

    ApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/image/upload")
    public Map<String, Object> uploadImageForCKEditor(@RequestParam("upload") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. Tạo tên file độc nhất để không bị trùng
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            
            // 2. Lưu file vào thư mục uploads/news/ của bạn
            Path uploadPath = Paths.get("uploads/news/");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

            // 3. Trả về JSON đúng chuẩn cấu trúc mà CKEditor mong muốn
            response.put("uploaded", 1);
            response.put("fileName", fileName);
            response.put("url", "/uploads/news/" + fileName); // Đường dẫn để web hiển thị ảnh
            
            return response;
        } catch (Exception e) {
            response.put("uploaded", 0);
            Map<String, String> error = new HashMap<>();
            error.put("message", "Lỗi tải ảnh: " + e.getMessage());
            response.put("error", error);
            return response;
        }
    }

    @GetMapping("/profile/{username}/articles")
    public Page<Article> loadMoreProfileArticles(@PathVariable String username, 
                                            @RequestParam(defaultValue = "0") int page, 
                                            @RequestParam(defaultValue = "3") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return articleRepository.findByAuthor(userRepository.findByUsername(username), pageable);
    }
}