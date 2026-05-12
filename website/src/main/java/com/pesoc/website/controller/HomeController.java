package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.pesoc.website.repository.*;

@Controller
public class HomeController {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TournamentRepository tournamentRepository; // Thêm repo này
    @Autowired
    private ArticleRepository articleRepository;
    
    @GetMapping({"/", "/home"})
    public String home(Model model) {
        model.addAttribute("top5Players", userRepository.findTop5ByOrderByEloDesc());

        model.addAttribute("upcomingMatches", matchRepository.findTop5ByUpcomingOrderByDateAsc(true));

        model.addAttribute("passedMatches", matchRepository.findTop5ByUpcomingOrderByDateDesc(false));
        model.addAttribute("latestNews", articleRepository.findTop5ByOrderByCreatedAtDesc());

        model.addAttribute("ongoingTournaments", tournamentRepository.findByOpeningTrue());
        model.addAttribute("currentYear", java.time.Year.now().getValue());

        return "home";
    }
}