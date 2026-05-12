package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.TournamentRepository;
import com.pesoc.website.repository.UserRepository;
import com.pesoc.website.service.AdminService;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {
    @Autowired
    private UserRepository userRepository;
    @Autowired 
    private MatchRepository matchRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private AdminService adminService;

    @GetMapping("/admin")
    public String admin(Model model){
        model.addAttribute("allUsers", userRepository.findAll());
        model.addAttribute("allUpcomingMatch", matchRepository.findByUpcoming(true));
        model.addAttribute("ongoingTournaments", tournamentRepository.findByOpeningTrue());
        return "admin";
    }

    @PostMapping("/admin/complete-match")
    public String completeMatch(@RequestParam("matchID") Long matchID, @RequestParam("score1") Integer score1, @RequestParam("score2") Integer score2, @RequestParam(value = "pen1", required = false) Integer pen1, @RequestParam(value = "pen2", required = false) Integer pen2, RedirectAttributes ra){
        adminService.completeMatch(matchID, score1, score2, pen1, pen2);

        ra.addFlashAttribute("message", "Cập nhật tỉ số thành công!");
        return "redirect:/admin";
    }
    
    @PostMapping("/admin/create-match")
    public String createMatch(@RequestParam("player1") String player1, @RequestParam("player2") String player2, RedirectAttributes ra){
        try{
            adminService.createMatch(player1, player2);
            ra.addFlashAttribute("message", "Tạo trận đấu mới thành công!");
        }
        catch(Exception e){
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/admin/add-player")
    public String addPlayer(@RequestParam("username") String username, @RequestParam("initialElo")Integer initialElo, RedirectAttributes ra){
        try{
            adminService.addPlayer(username, initialElo);
            ra.addFlashAttribute("message", "Thêm người chơi thành công!");
        }
        catch(Exception e){
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/admin";
    }

    @PostMapping("/admin/create-tournament")
    public String createTournament(@RequestParam("name") String name, @RequestParam("type") String type, RedirectAttributes ra){
        try{
            adminService.createTournament(name, type);
            ra.addFlashAttribute("message", "Tạo giải đấu thành công!");
        }
        catch(Exception e){
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }   

        return "redirect:/admin";
    }
}
