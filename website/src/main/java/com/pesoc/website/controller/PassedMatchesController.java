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
public class PassedMatchesController {
    @Autowired
    private MatchRepository matchRepository;
    
    @GetMapping("/passed-matches")
    public String PassedMatches(
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model) {
            
        // Mỗi trang load 10 trận
        int pageSize = 10; 
        Pageable pageable = PageRequest.of(page, pageSize);
        
        // Lấy dữ liệu dạng Page<Match> thay vì List<Match>
        Page<Match> matchPage = matchRepository.findByUpcomingOrderByDateDesc(false, pageable);
        
        // Truyền đối tượng Page xuống view
        model.addAttribute("matchPage", matchPage);
        
        return "passed-matches"; 
    }
}