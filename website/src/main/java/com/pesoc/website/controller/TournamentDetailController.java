package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.pesoc.website.model.Match;
import com.pesoc.website.model.PlayerStatDTO;
import com.pesoc.website.model.Tournament;
import com.pesoc.website.model.TournamentRanking;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.TournamentRankingRepository;
import com.pesoc.website.repository.TournamentRepository;
import com.pesoc.website.repository.UserRepository;
import com.pesoc.website.service.TournamentDetailService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;


@Controller
public class TournamentDetailController {
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRankingRepository rankingRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TournamentDetailService tournamentDetailService; 

    @GetMapping("/tournament-detail/{name}")
    public String tournamentDetail(@PathVariable("name") String name, Model model){
        Tournament tournament = tournamentRepository.findByName(name);
        if (tournament == null){
            return "redirect:/home";
        }
        model.addAttribute("tournament", tournament);
        String type = tournament.getTournamentType();

        List<TournamentRanking> allRankings = rankingRepository.findByTournamentOrderByPointsDescDifferenceDescGoalsDesc(tournament);

        List<String> registeredUsernames = allRankings.stream().filter(r -> r.getUser() != null) .map(r -> r.getUser().getUsername()).collect(Collectors.toList());

        List<User> allUsers = userRepository.findAll();
        List<User> unregisteredUsers = allUsers.stream().filter(u -> u.getUsername() != null && !registeredUsernames.contains(u.getUsername())).collect(Collectors.toList());

        model.addAttribute("unregisteredUsers", unregisteredUsers);

        if("LEAGUE".equals(type) || "MIXED".equals(type)){
            Map<String, List<TournamentRanking>> rankingsByGroup = allRankings.stream().collect(Collectors.groupingBy(r -> (r.getGroupName() != null && !r.getGroupName().isEmpty()) ? r.getGroupName() : "⏳ DANH SÁCH CHỜ", LinkedHashMap::new, Collectors.toList()));
            
            model.addAttribute("rankingsByGroup", rankingsByGroup);

            List<User> registeredUsers = allRankings.stream().map(TournamentRanking::getUser).collect(Collectors.toList());
            
            model.addAttribute("registeredUsers", registeredUsers);

            List<Match> leagueMatches = matchRepository.findByTournamentAndMatchTypeOrderByRoundNumberAscDateDesc(tournament, "GROUP");
            
            // 🌟 ĐÃ SỬA: Dùng TreeMap với Collections.reverseOrder() để lật ngược thứ tự Vòng Bảng (VD: Vòng 3 -> Vòng 2 -> Vòng 1)
            Map<Integer, List<Match>> matchesByRound = leagueMatches.stream()
                .collect(Collectors.groupingBy(
                    Match::getRoundNumber, 
                    () -> new java.util.TreeMap<>(java.util.Collections.reverseOrder()), 
                    Collectors.toList()
                ));
            
            model.addAttribute("matchesByRound", matchesByRound);
        }

        if("KNOCKOUT".equals(type) || "MIXED".equals(type)){
            List<Match> knockoutMatches = matchRepository.findByTournamentAndMatchTypeOrderByRoundNumberAscDateDesc(tournament, "KNOCKOUT");
            
            // Bước 1: Vẫn lấy ra thứ tự gốc (VD: Tứ Kết -> Bán Kết -> Chung Kết)
            Map<String, List<Match>> tempMatchesByPhase = knockoutMatches.stream()
                .collect(Collectors.groupingBy(Match::getPhaseName, LinkedHashMap::new, Collectors.toList()));
            
            // 🌟 ĐÃ SỬA: Bước 2 - Lật ngược thủ công để biến thành (Chung Kết -> Bán Kết -> Tứ Kết)
            Map<String, List<Match>> matchesByPhase = new LinkedHashMap<>();
            java.util.List<String> keys = new java.util.ArrayList<>(tempMatchesByPhase.keySet());
            java.util.Collections.reverse(keys); // Lật ngược danh sách Key
            for (String key : keys) {
                matchesByPhase.put(key, tempMatchesByPhase.get(key));
            }
            
            model.addAttribute("knockoutMatches", matchesByPhase);
        }

        // --- THÊM PHẦN LẤY THỐNG KÊ TOP 5 ---
        Map<String, List<PlayerStatDTO>> topStats = tournamentDetailService.getTop5Stats(tournament);
        model.addAttribute("topScorers", topStats.get("topScorers"));
        model.addAttribute("topCleanSheets", topStats.get("topCleanSheets"));
        model.addAttribute("topConceded", topStats.get("topConceded"));
        
        return "tournament-detail"; // Dòng cũ của sếp
    }
    
    @PostMapping("/admin/tournament-detail/{name}/add-match")
    public String addMatch(@PathVariable("name") String tName, @RequestParam("p1") String p1, @RequestParam("p2") String p2, @RequestParam("matchType") String matchType, @RequestParam(value = "phaseName", defaultValue = "") String phaseName, @RequestParam("roundNumber") Integer roundNumber, RedirectAttributes ra) {
        try {
            tournamentDetailService.addMatch(tName, p1, p2, matchType, phaseName, roundNumber);
            ra.addFlashAttribute("message", "Thêm trận đấu thành công!");
        } 
        catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }
        
        return "redirect:/tournament-detail/" + tName;
    }

    @PostMapping("/admin/tournament-detail/{name}/add-player")
    public String addPlayerToGroup(@PathVariable("name") String tName, @RequestParam("username") String username, @RequestParam("groupName") String groupName, RedirectAttributes ra){
        try{
            tournamentDetailService.addPlayerToGroup(tName, username, groupName);
            ra.addFlashAttribute("message", "Đã thêm " + username + " vào " + groupName);
        }      
        catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/tournament-detail/" + tName;

    }
    
    @PostMapping("/tournament-detail/{name}/register")
    public String register(@PathVariable("name") String tName, HttpSession session, RedirectAttributes ra){
        User loggedInUser = (User) session.getAttribute("logInUser");

        if(loggedInUser == null) {
            ra.addFlashAttribute("message", "Vui lòng đăng nhập để đăng ký!");
        }

        try{
            tournamentDetailService.registerPlayer(tName, loggedInUser.getUsername());
            ra.addFlashAttribute("message", "Đăng ký tham gia thành công! Chờ Admin xếp bảng nhé.");
        } 
        catch(Exception e){
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/tournament-detail/" + tName;
    }
    
    @PostMapping("/admin/tournament-detail/{name}/update-final-rank")
    public String updateFinalRank(@PathVariable("name") String tName, @RequestParam("rankingId") Long rankingId, @RequestParam("finalRank") Integer finalRank, RedirectAttributes ra){
        try{
            tournamentDetailService.updateFinalRank(rankingId, finalRank);
            ra.addFlashAttribute("message", "Đã cập nhật thứ hạng thành công!");
        } 
        catch(Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/tournament-detail/" + tName;
    }

    @PostMapping("/admin/tournament-detail/{name}/end-tournament")
    public String endTournament(@PathVariable("name") String tName, RedirectAttributes ra){
        try{
            tournamentDetailService.endTournament(tName);
            ra.addFlashAttribute("message", "Đã kết thúc giải đấu!");
        }
        catch(Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }

        return "redirect:/tournament-detail/" + tName;
    }

    @PostMapping("/admin/tournament-detail/{name}/upload-lineup")
    public String uploadLineup(@PathVariable("name") String tName, 
                               @RequestParam("username") String username, 
                               @RequestParam("lineupFile") MultipartFile file, 
                               RedirectAttributes ra) {
        try {
            tournamentDetailService.uploadLineup(tName, username, file);
            ra.addFlashAttribute("message", "Cập nhật đội hình cho " + username + " thành công!");
        } 
        catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi: " + e.getMessage());
        }
        return "redirect:/tournament-detail/" + tName;
    }

    @PostMapping("/admin/tournament-detail/{name}/upload-banner")
    public String uploadTournamentBanner(@PathVariable("name") String tName, 
                                        @RequestParam("bannerFile") MultipartFile file, 
                                        RedirectAttributes redirectAttributes) {
        
        // 1. Tìm giải đấu trong DB
        Tournament tournament = tournamentRepository.findByName(tName);
        if (tournament == null) return "redirect:/tournament-detail/" + tName;
        if (!file.isEmpty()) {
            try {
                // Đặt tên file ngẫu nhiên để không bị trùng (tránh lỗi cache)
                String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                
                // Đường dẫn lưu file (b nhớ tạo thư mục uploads/tournaments trong thư mục gốc hoặc static nhé)
                Path uploadPath = Paths.get("uploads/tournaments/");
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }
                
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                
                // 3. Cập nhật tên ảnh vào DB
                tournament.setBanner(fileName);
                tournamentRepository.save(tournament);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 4. Load lại trang chi tiết giải đấu (Nhớ dùng RedirectAttributes để tránh lỗi font chữ)
        redirectAttributes.addAttribute("tourName", tournament.getName());
        return "redirect:/tournament-detail/{tourName}"; // Điều chỉnh lại link return theo đúng route của b
    }

    @PostMapping("/admin/tournament-detail/{name}/sync-titles")
    public String syncTitles(@PathVariable("name") String tName, RedirectAttributes ra) {
        try {
            Tournament tournament = tournamentRepository.findByName(tName);
            if (tournament == null) {
                throw new RuntimeException("Giải đấu không tồn tại!");
            }
            
            // Gọi hàm tính toán lại danh hiệu sếp đã viết sẵn
            tournamentDetailService.updateDynamicTitles(tournament);
            
            ra.addFlashAttribute("message", "Thành công: Đã đồng bộ lại toàn bộ danh hiệu cho giải đấu này!");
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi đồng bộ: " + e.getMessage());
        }

        // Chạy xong thì quay lại trang chi tiết giải đấu đó
        return "redirect:/tournament-detail/" + tName;
    }
}