package com.pesoc.website.controller;

import com.pesoc.website.repository.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.pesoc.website.model.Article;
import com.pesoc.website.model.EloHistory;
import com.pesoc.website.model.Match;
import com.pesoc.website.model.TournamentRanking;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.ArticleRepository;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.TournamentRankingRepository;
import com.pesoc.website.service.ProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
public class ProfileController {
    private final UserRepository userRepository;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private TournamentRankingRepository rankingRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private ArticleRepository articleRepository;

    ProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/profile/{username}")
    public String profile(@PathVariable String username, HttpSession session, Model model){
        User user = profileService.getProfile(username);
        
        if (user == null){
            return "redirect:/?error=not_found";
        }

        User loggedInUser = (User) session.getAttribute("logInUser");
        boolean isOwner = profileService.checkIfOwner(loggedInUser, username);

        model.addAttribute("user", user);
        model.addAttribute("isOwner", isOwner);

        List<TournamentRanking> allRankings = rankingRepository.findByUser(user);
        List<TournamentRanking> finishedTours = allRankings.stream().filter(r -> !r.getTournament().isOpening()).collect(Collectors.toList());
        List<TournamentRanking> upcomingTours = allRankings.stream().filter(r -> r.getTournament().isOpening()).collect(Collectors.toList());
        
        List<Match> allMatches = matchRepository.findAllByPlayer(user); 
        List<Match> recentMatches = allMatches.stream().filter(m -> !m.isUpcoming()).limit(5).collect(Collectors.toList());
        List<Match> upcomingMatches = allMatches.stream().filter(m -> m.isUpcoming()).collect(Collectors.toList());

        model.addAttribute("finishedTours", finishedTours);
        model.addAttribute("upcomingTours", upcomingTours);
        model.addAttribute("recentMatches", recentMatches);
        model.addAttribute("upcomingMatches", upcomingMatches);


        List<Match> finishedMatches = allMatches.stream().filter(m -> !m.isUpcoming()).collect(Collectors.toList());

        int total = finishedMatches.size();
        int wins = 0, draws = 0, losses = 0;
        List<String> form = new ArrayList<>();

        for(Match m : finishedMatches){
            boolean isP1 = m.getPlayer1().getId().equals(user.getId());
            int myScore = isP1 ? m.getPlayer1Score() : m.getPlayer2Score();
            int opScore = isP1 ? m.getPlayer2Score() : m.getPlayer1Score();

            if (myScore > opScore) {
                wins++;
                if (form.size() < 5) form.add("W");
            } 
            else if (myScore < opScore) {
                losses++;
                if (form.size() < 5) form.add("L");
            } 
            else {
                draws++;
                if (form.size() < 5) form.add("D");
            }
        }

        double winRate = (total > 0) ? ((double) wins / total) * 100 : 0;

        model.addAttribute("totalMatches", total);
        model.addAttribute("wins", wins);
        model.addAttribute("draws", draws);
        model.addAttribute("losses", losses);
        model.addAttribute("winRate", String.format("%.1f", winRate));
        model.addAttribute("form", form);

        List<EloHistory> eloHistoryASC = user.getEloHistories().stream().sorted(Comparator.comparing(EloHistory::getChangeDate)).collect(Collectors.toList());

        int peakElo = eloHistoryASC.stream().mapToInt(EloHistory::getElo).max().orElse(user.getElo());

        int lowestElo = eloHistoryASC.stream().mapToInt(EloHistory::getElo).min().orElse(user.getElo());

        List<Map<String, Object>> eloTableRows = new ArrayList<>();
        for (int i = eloHistoryASC.size() - 1; i >= 0; i--){
            Map<String, Object> row = new HashMap<>();
            EloHistory current = eloHistoryASC.get(i);
            row.put("elo", current.getElo());
            row.put("date", current.getChangeDate());
            row.put("note", current.getNote());

            String diff = "-";
            if (i > 0) {
                int change = current.getElo() - eloHistoryASC.get(i - 1).getElo();
                diff = (change >= 0) ? "+" + change : String.valueOf(change);
            }
            row.put("diff", diff);
            eloTableRows.add(row);
        }

        model.addAttribute("user", user);
        model.addAttribute("peakElo", peakElo);
        model.addAttribute("lowestElo", lowestElo);
        model.addAttribute("eloChartLabels", eloHistoryASC.stream().map(h -> h.getChangeDate().toString()).collect(Collectors.toList()));
        model.addAttribute("eloChartData", eloHistoryASC.stream().map(EloHistory::getElo).collect(Collectors.toList()));
        model.addAttribute("eloTableRows", eloTableRows);

        // Lấy danh sách tất cả người chơi sắp xếp theo Elo giảm dần
        List<User> allRankedPlayers = userRepository.findAllByOrderByEloDesc();
        int globalRank = 0;
        for (int i = 0; i < allRankedPlayers.size(); i++) {
            if (allRankedPlayers.get(i).getId().equals(user.getId())) {
                globalRank = i + 1;
                break;
            }
        }
        model.addAttribute("globalRank", globalRank);
        
        Pageable pageable = PageRequest.of(0, 3, Sort.by("createdAt").descending());
        Page<Article> authorArticles = articleRepository.findByAuthor(userRepository.findByUsername(username), pageable);
        model.addAttribute("authorArticles", authorArticles);
        return "profile";
    }

    @PostMapping("/profile/{username}/update-pes-username")
    public String UpdatePesUsername(@PathVariable("username") String username, @RequestParam("newPesUsername") String name, HttpSession session, RedirectAttributes ra) {
        try {
            User loggedInUser = (User) session.getAttribute("logInUser"); 

            if(profileService.checkIfOwner(loggedInUser, username)) { 
                User targetUser = profileService.getProfile(username);
                targetUser.setPesUsername(name);
                userRepository.save(targetUser);
                ra.addFlashAttribute("message", "Đổi bio thành công!");
            } 
            else {
                ra.addFlashAttribute("message", "Bạn không có quyền thay đổi!");
            }
        } 
        catch(Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        ra.addAttribute("userUrl", username); 
        return "redirect:/profile/{userUrl}";
    }
    
    @PostMapping("/profile/{username}/update-description")
    public String UpdateDescription(@PathVariable("username") String username, @RequestParam("newDescription") String description, HttpSession session, RedirectAttributes ra){
        try{
            User loggedInUser = (User) session.getAttribute("logInUser"); 

            if(profileService.checkIfOwner(loggedInUser, username)) { 
                User targetUser = profileService.getProfile(username);
                targetUser.setDescription(description);
                userRepository.save(targetUser);
                ra.addFlashAttribute("message", "Đổi bio thành công!");
            } 
            else {
                ra.addFlashAttribute("message", "Bạn không có quyền thay đổi!");
            }
        } 
        catch(Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        ra.addAttribute("userUrl", username); 
        return "redirect:/profile/{userUrl}";
    }

    @PostMapping("/profile/{username}/upload-avatar")
    public String uploadAvatar(@PathVariable("username") String username, 
                                @RequestParam("avatarFile") MultipartFile file, 
                                HttpSession session, RedirectAttributes ra) {
        try {
            User loggedInUser = (User) session.getAttribute("logInUser");
            if (profileService.checkIfOwner(loggedInUser, username)) {
                    
                if (!file.isEmpty()) {
                    User targetUser = profileService.getProfile(username);
                        
                    // 1. Tạo thư mục uploads/avatars ở ngoài thư mục dự án
                    String uploadDir = "uploads/avatars/";
                    Path uploadPath = Paths.get(uploadDir);
                    if (!Files.exists(uploadPath)) {
                        Files.createDirectories(uploadPath); // Tự tạo thư mục nếu chưa có
                    }

                    // 2. TỰ ĐỘNG XÓA ẢNH CŨ NẾU CÓ
                    if (targetUser.getAvatar() != null) {
                        Path oldFilePath = uploadPath.resolve(targetUser.getAvatar());
                        Files.deleteIfExists(oldFilePath);
                    }

                    // 3. Đổi tên file để tránh trùng lặp (Ví dụ: 123e4567-e89b..._anh1.jpg)
                    String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                    Path filePath = uploadPath.resolve(filename);
                        
                    // 4. Lưu file mới vào ổ cứng
                    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

                    // 5. Cập nhật tên ảnh vào Database
                    targetUser.setAvatar(filename);
                    userRepository.save(targetUser);

                    // Cập nhật lại ảnh cho session đang đăng nhập để thẻ Header đổi ảnh ngay lập tức
                    if (loggedInUser.getUsername().equals(username)) {
                        session.setAttribute("logInUser", targetUser);
                    }

                    ra.addFlashAttribute("message", "Cập nhật ảnh đại diện thành công!");
                }
            } 
            else {
                ra.addFlashAttribute("message", "Bạn không có quyền thay đổi!");
            }
        }

        catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }            
        ra.addAttribute("userUrl", username); 
        return "redirect:/profile/{userUrl}";
    }

    @PostMapping("/profile/{username}/change-password")
    public String changePassword(@PathVariable("username") String username, 
                                 @RequestParam("newPassword") String newPassword, 
                                 HttpSession session, RedirectAttributes ra) {
        try {
            User loggedInUser = (User) session.getAttribute("logInUser"); 

            if(profileService.checkIfOwner(loggedInUser, username)) { 
                User targetUser = profileService.getProfile(username);
                targetUser.setPassword(newPassword); // Set mật khẩu mới
                userRepository.save(targetUser); // Lưu vào Database
                ra.addFlashAttribute("message", "Đổi mật khẩu thành công! Hãy ghi nhớ pass mới nhé.");
            } 
            else {
                ra.addFlashAttribute("message", "Bạn không có quyền đổi mật khẩu của người này!");
            }
        } 
        catch(Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        ra.addAttribute("userUrl", username); 
        return "redirect:/profile/{userUrl}";
    }

}