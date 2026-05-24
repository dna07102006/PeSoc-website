package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.TournamentRepository;
import com.pesoc.website.repository.UserRepository;

@Controller
public class AboutUsController {
    @Autowired private UserRepository userRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private TournamentRepository tournamentRepository;

    @GetMapping("/about-us")
    public String aboutUs(Model model) {
        // Đếm tổng số dữ liệu thật trong Database và truyền ra view
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalMatches", matchRepository.count());
        model.addAttribute("totalTournaments", tournamentRepository.count());
        model.addAttribute("user1", userRepository.findByUsername("Nam"));
        model.addAttribute("user2", userRepository.findByUsername("Hoàng Đức"));
        model.addAttribute("user3", userRepository.findByUsername("Hiếu"));
        model.addAttribute("user4", userRepository.findByUsername("Tuấn"));
        model.addAttribute("user5", userRepository.findByUsername("Phong"));
        model.addAttribute("user6", userRepository.findByUsername("Hải Anh"));
        model.addAttribute("user7", userRepository.findByUsername("Liêm"));
        model.addAttribute("user8", userRepository.findByUsername("ChingChong"));
        model.addAttribute("user9", userRepository.findByUsername("Đạt"));
        model.addAttribute("user10", userRepository.findByUsername("Triệu"));
        model.addAttribute("user11", userRepository.findByUsername("Hưng Lê"));
        model.addAttribute("user12", userRepository.findByUsername("Đăng"));
        return "about-us";
    }
}