package com.pesoc.website.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.pesoc.website.model.Tag;
import com.pesoc.website.repository.TagRepository;
import com.pesoc.website.repository.TournamentRepository;
import com.pesoc.website.repository.UserRepository;

@RestController 
@RequestMapping("/api")
public class SearchController {
    @Autowired private UserRepository userRepository;
    @Autowired private TournamentRepository tournamentRepository;
    @Autowired private TagRepository tagRepository;

    @GetMapping("/search")
    public Map<String, Object> quickSearch(@RequestParam String keyword) {
        Map<String, Object> results = new HashMap<>();
        String cleanKw = keyword.trim();

        // 1. Trả về list chứa mỗi 'username' để bảo mật mật khẩu
        results.put("users", userRepository.searchUsers(cleanKw).stream()
                .map(u -> Map.of("username", u.getUsername()))
                .toList());

        // 2. Trả về list chứa mỗi 'name' của giải đấu
        results.put("tournaments", tournamentRepository.searchTournaments(cleanKw).stream()
                .map(t -> Map.of("name", t.getName()))
                .toList());

        // 3. Trả về list các chuỗi tên Tag (Ở đoạn cũ b dùng 'keyword' thay vì 'cleanKw', mình sửa lại luôn cho mượt)
        results.put("tags", tagRepository.findByNameContainingIgnoreCase(cleanKw).stream()
                .map(Tag::getName)
                .toList());

        return results;
    }
}