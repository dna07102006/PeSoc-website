package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.pesoc.website.model.Tournament;
import com.pesoc.website.repository.TournamentRepository;

import java.util.List;

@Controller
public class TournamentsController {
    @Autowired
    private TournamentRepository tournamentRepository;

    @GetMapping("/tournaments")
    public String tournaments(@RequestParam(name = "page", defaultValue = "0") int page, Model model) {
        
        // 1. Lấy tất cả giải ĐANG diễn ra (Thường rất ít nên dùng List)
        List<Tournament> openingTournaments = tournamentRepository.findByOpeningOrderByOpenDateDesc(true);

        // 2. Lấy giải ĐÃ kết thúc và PHÂN TRANG (10 giải / 1 trang)
        Pageable pageable = PageRequest.of(page, 10);
        Page<Tournament> passedTournaments = tournamentRepository.findByOpeningOrderByFinishDateDesc(false, pageable);
        
        model.addAttribute("openingTournaments", openingTournaments);
        model.addAttribute("passedTournaments", passedTournaments);

        return "tournaments"; 
    }
}