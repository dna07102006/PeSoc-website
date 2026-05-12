package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.pesoc.website.repository.UserRepository;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.model.User;
import com.pesoc.website.model.Match;
import com.pesoc.website.model.EloHistory;
import java.util.*;


@Controller
public class EloBoardController {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private MatchRepository matchRepository;

    @GetMapping("/elo-board")
    public String EloBoard(@RequestParam(defaultValue = "desc") String sort, Model model) {
        List<User> players = "asc".equals(sort) ? userRepository.findAllByOrderByEloAsc() : userRepository.findAllByOrderByEloDesc();
        List<Map<String, Object>> playerStats = new ArrayList<>();
        
        User bestGrower = null;
        User worstGrower = null;
        int maxGrowth = 0; 
        int minGrowth = 0; 
        java.time.LocalDateTime sevenDaysAgo = java.time.LocalDateTime.now().minusDays(7);

        for (User user : players) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("player", user); // Chắc chắn đưa user vào list

            try {
                // 1. PHONG ĐỘ (Max 5 trận)
                List<Match> matches = matchRepository.findAllByPlayer(user).stream()
                        .filter(m -> !m.isUpcoming())
                        .sorted(java.util.Comparator.comparing(Match::getDate).reversed())
                        .limit(5).collect(java.util.stream.Collectors.toList());
                
                List<String> form = new ArrayList<>();
                for (Match m : matches) {
                    boolean isP1 = m.getPlayer1().getId().equals(user.getId());
                    int myS = isP1 ? m.getPlayer1Score() : m.getPlayer2Score();
                    int opS = isP1 ? m.getPlayer2Score() : m.getPlayer1Score();
                    form.add(myS > opS ? "W" : (myS < opS ? "L" : "D"));
                }
                stats.put("form", form);

                // 2. TRẬN GẦN NHẤT
                if (!matches.isEmpty()) {
                    Match last = matches.get(0);
                    boolean isP1 = last.getPlayer1().getId().equals(user.getId());
                    
                    // TRUYỀN CỐ ĐỊNH PLAYER 1 (Trên) VÀ PLAYER 2 (Dưới)
                    stats.put("p1Name", last.getPlayer1().getUsername());
                    stats.put("p2Name", last.getPlayer2().getUsername());
                    stats.put("p1Score", last.getPlayer1Score());
                    stats.put("p2Score", last.getPlayer2Score());
                    
                    // Xác định kết quả W, D, L cho icon của người đang xét (Dựa vào myScore và opScore)
                    int myS = isP1 ? last.getPlayer1Score() : last.getPlayer2Score();
                    int opS = isP1 ? last.getPlayer2Score() : last.getPlayer1Score();
                    String lastRes = (myS > opS) ? "W" : ((myS < opS) ? "L" : "D");
                    stats.put("lastResult", lastRes);
                    
                    // Tính Elo thay đổi (+/-) của trận cuối
                    int lastDiff = 0;
                    List<EloHistory> histDesc = user.getEloHistories().stream()
                        .sorted(java.util.Comparator.comparing(EloHistory::getChangeDate).reversed())
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (histDesc.size() >= 2) {
                        lastDiff = histDesc.get(0).getElo() - histDesc.get(1).getElo();
                    }
                    stats.put("lastDiff", lastDiff);
                }

                // 3. TÍNH TOÁN GROWTH (Fix lỗi nhảy số ảo)
                List<EloHistory> history = user.getEloHistories();
                if (history != null && !history.isEmpty()) {
                    int currentElo = user.getElo();
                    int oldElo = history.stream()
                            .filter(h -> h.getChangeDate().isBefore(sevenDaysAgo))
                            .sorted(java.util.Comparator.comparing(EloHistory::getChangeDate).reversed())
                            .map(EloHistory::getElo).findFirst()
                            .orElse(history.stream()
                                    .sorted(java.util.Comparator.comparing(EloHistory::getChangeDate))
                                    .map(EloHistory::getElo).findFirst().get());

                    int growth = currentElo - oldElo;
                    if (growth > maxGrowth) { maxGrowth = growth; bestGrower = user; }
                    if (growth < minGrowth) { minGrowth = growth; worstGrower = user; }
                }
            } catch (Exception e) {
                // Nếu lỗi ở bước tính toán nào đó, vẫn giữ user trong list với stats mặc định
                if (!stats.containsKey("form")) stats.put("form", new ArrayList<>());
            }
            playerStats.add(stats);
        }

        model.addAttribute("sort", sort);
        model.addAttribute("playerStats", playerStats);
        model.addAttribute("bestGrower", bestGrower);
        model.addAttribute("maxGrowth", maxGrowth);
        model.addAttribute("worstGrower", worstGrower);
        model.addAttribute("minGrowth", minGrowth);

        return "elo-board";
    }
}