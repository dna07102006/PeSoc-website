package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.pesoc.website.model.Match;
import com.pesoc.website.repository.MatchRepository;

@Controller
public class UpcomingMatchesController {
    @Autowired
    private MatchRepository matchRepository;
    
    @GetMapping("/upcoming-matches")
    public String upComingMatches(
            @RequestParam(name = "page", defaultValue = "0") int page, 
            Model model) {
        
        // Thiết lập mỗi trang hiển thị 10 bản ghi
        Pageable pageable = PageRequest.of(page, 10);
        
        // Lấy dữ liệu dạng Page thay vì List
        Page<Match> matchPage = matchRepository.findByUpcomingOrderByIdAsc(true, pageable);
        
        // Truyền cả đối tượng matchPage xuống HTML
        model.addAttribute("matchPage", matchPage);
        
        return "upcoming-matches"; 
    }
}