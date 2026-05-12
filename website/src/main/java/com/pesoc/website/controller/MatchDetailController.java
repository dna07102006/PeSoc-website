package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.pesoc.website.model.Match;
import com.pesoc.website.model.TournamentRanking;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.TournamentRankingRepository;

import java.util.List;

@Controller
public class MatchDetailController {

    @Autowired
    private MatchRepository matchRepository;
    
    @Autowired
    private TournamentRankingRepository rankingRepository;

    @GetMapping("/match-detail/{id}")
    public String matchDetail(@PathVariable Long id, Model model) {
        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) return "redirect:/";

        // 1. Lấy lịch sử đối đầu (H2H)
        List<Match> h2hMatches = matchRepository.findHeadToHead(match.getPlayer1().getId(), match.getPlayer2().getId());
        
        long p1Wins = 0, draws = 0, p2Wins = 0;
        for (Match m : h2hMatches) {
            if (m.getPlayer1Score() != null && m.getPlayer2Score() != null) {
                // Xác định xem trong trận cũ này, người nào tương ứng với Player 1 của trận hiện tại
                boolean isP1First = m.getPlayer1().getId().equals(match.getPlayer1().getId());
                int s1 = isP1First ? m.getPlayer1Score() : m.getPlayer2Score();
                int s2 = isP1First ? m.getPlayer2Score() : m.getPlayer1Score();
                
                if (s1 > s2) p1Wins++;
                else if (s1 < s2) p2Wins++;
                else draws++;
            }
        }

        // 2. Lấy đội hình (Lineup Image) từ TournamentRanking
        String p1Lineup = null;
        String p2Lineup = null;
        
        if (match.getTournament() != null) {
            TournamentRanking r1 = rankingRepository.findByTournamentAndUser(match.getTournament(), match.getPlayer1());
            TournamentRanking r2 = rankingRepository.findByTournamentAndUser(match.getTournament(), match.getPlayer2());
            
            if (r1 != null) p1Lineup = r1.getLineupImage();
            if (r2 != null) p2Lineup = r2.getLineupImage();
        }

        model.addAttribute("match", match);
        model.addAttribute("h2hMatches", h2hMatches);
        model.addAttribute("p1Wins", p1Wins);
        model.addAttribute("draws", draws);
        model.addAttribute("p2Wins", p2Wins);
        model.addAttribute("p1Lineup", p1Lineup);
        model.addAttribute("p2Lineup", p2Lineup);

        return "match-detail";
    }
}