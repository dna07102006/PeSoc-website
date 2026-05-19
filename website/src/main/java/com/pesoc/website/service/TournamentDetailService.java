package com.pesoc.website.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pesoc.website.model.Match;
import com.pesoc.website.model.PlayerStatDTO;
import com.pesoc.website.model.Subscription;
import com.pesoc.website.model.Tournament;
import com.pesoc.website.model.TournamentRanking;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.MatchRepository;
import com.pesoc.website.repository.SubscriptionRepository;
import com.pesoc.website.repository.TournamentRankingRepository;
import com.pesoc.website.repository.TournamentRepository;
import com.pesoc.website.repository.UserRepository;

@Service
public class TournamentDetailService {
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TournamentRankingRepository rankingRepository;
    @Autowired
    private FirebaseService firebaseService;
    @Autowired 
    private SubscriptionRepository subscriptionRepository;
    @Autowired private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Autowired private com.pesoc.website.repository.InAppNotificationRepository inAppNotificationRepository;

    @Transactional
    public void addMatch(String tName, String p1, String p2, String matchType, String phaseName, Integer roundNumber) {
        Tournament tournament = tournamentRepository.findByName(tName);
        User user1 = userRepository.findByUsername(p1);
        User user2 = userRepository.findByUsername(p2);

        if (user1 == null || user2 == null) {
            throw new RuntimeException("Không tìm thấy người chơi!");
        }
        if (user1.getId().equals(user2.getId())) {
            throw new RuntimeException("Một người không thể tự đá với chính mình!");
        }

        // --- 1. XÁC ĐỊNH TÊN VÒNG ĐẤU TRƯỚC ĐỂ ĐI CHECK ---
        String actualPhaseName = ("GROUP".equals(matchType) && (phaseName == null || phaseName.isEmpty())) 
            ? "Vòng " + roundNumber 
            : phaseName;

        // --- 2. KIỂM TRA XEM ĐÂY CÓ PHẢI VÒNG ĐẤU HOÀN TOÀN MỚI KHÔNG ---
        boolean isNewPhase = false;
        if (tournament != null) {
            // Nếu TRƯỚC KHI LƯU mà DB chưa từng có trận nào mang tên phase này -> Đây chính là trận đầu tiên của vòng mới!
            isNewPhase = !matchRepository.existsByTournamentAndPhaseName(tournament, actualPhaseName);
        }

        // --- 3. TIẾN HÀNH TẠO VÀ LƯU TRẬN ĐẤU NHƯ CŨ ---
        Match match = new Match();
        match.setTournament(tournament);
        match.setPlayer1(user1);
        match.setPlayer2(user2);
        match.setUpcoming(true);
        match.setMatchType(matchType);
        match.setPhaseName(actualPhaseName); 
        match.setRoundNumber(roundNumber);
        matchRepository.save(match);

        // --- 4. CHỈ BẮN THÔNG BÁO NẾU LÀ TRẬN ĐẦU TIÊN CỦA VÒNG MỚI ---
        if (tournament != null && isNewPhase) {
            firebaseService.sendToSubscribers(
                tournament.getName(), 
                "TOURNAMENT", 
                "Đã có lịch thi đấu " + actualPhaseName + "! 🗓️", 
                "Bạn có cuộc hẹn với " + tournament.getName() + ": Lịch thi đấu " + actualPhaseName + "!", "/tournament-detail/" + tName
            );

            List<Subscription> subs = subscriptionRepository.findByTargetIdAndTargetType(tName, "TOURNAMENT");
            for(Subscription sub : subs){
                String subName = sub.getUser().getUsername();
                sendInAppNotification(subName, "Đã có lịch thi đấu " + actualPhaseName + "! 🗓️", "Bạn có cuộc hẹn với " + tournament.getName() + ": Lịch thi đấu " + actualPhaseName + "!", "/tournament-detail/" + tName);
            }
        }
    }

    @Transactional
    public void addPlayerToGroup(String tName, String username, String groupName) {
        Tournament t = tournamentRepository.findByName(tName);
        User u = userRepository.findByUsername(username);

        if (t == null || u == null) {
            throw new RuntimeException("Giải đấu hoặc Người chơi không tồn tại!");
        }

        TournamentRanking ranking = rankingRepository.findByTournamentAndUser(t, u);
        if(ranking == null) {
            throw new RuntimeException("Người chơi này không có tên trong giải đấu!");
        }

        ranking.setGroupName(groupName);
        rankingRepository.save(ranking);
    }

    @Transactional
    public void registerPlayer(String tName, String username) {
        Tournament t = tournamentRepository.findByName(tName);
        User u = userRepository.findByUsername(username);

        if (t == null || u == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ!");
        }

        TournamentRanking ranking = rankingRepository.findByTournamentAndUser(t, u);
        if(ranking != null){
            throw new RuntimeException("Bạn đã đăng ký giải đấu này rồi!");
        }

        ranking = new TournamentRanking();
        ranking.setTournament(t);
        ranking.setUser(u); 
        ranking.setPoints(0);
        ranking.setMatchesPlayed(0);
        ranking.setWins(0);
        ranking.setDraws(0);
        ranking.setLosses(0);
        ranking.setGoals(0);
        ranking.setDifference(0);
        ranking.setConceded(0);
        rankingRepository.save(ranking);
    }

    @Transactional
    public void updateFinalRank(Long rankingId, Integer rank) {
        TournamentRanking ranking = rankingRepository.findById(rankingId)
            .orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu xếp hạng!")); 
        
        ranking.setFinalRank(rank); 
        rankingRepository.save(ranking); 

        if (rank != null && rank == 1) {
            Tournament tournament = ranking.getTournament();
            String tName = tournament.getName();
            String championName = ranking.getUser().getUsername();

            List<TournamentRanking> allRankings = rankingRepository.findByTournamentOrderByPointsDescDifferenceDescGoalsDesc(tournament);
            
            List<String> topScorers = new java.util.ArrayList<>();
            List<String> goldenGloves = new java.util.ArrayList<>();

            for (TournamentRanking r : allRankings) {
                if (Boolean.TRUE.equals(r.isTopScorer())) {
                    topScorers.add(r.getUser().getUsername());
                }
                if (Boolean.TRUE.equals(r.isGoldenGlove())) {
                    goldenGloves.add(r.getUser().getUsername());
                }
            }

            String vualuoiStr = topScorers.isEmpty() ? "Chưa rõ" : String.join(", ", topScorers);
            String gangtayStr = goldenGloves.isEmpty() ? "Chưa rõ" : String.join(", ", goldenGloves);

            // Phát loa diện rộng tới toàn bộ anh em bật chuông theo dõi giải này
            firebaseService.sendToSubscribers(
                tName, 
                "TOURNAMENT", 
                "NHÀ VÔ ĐỊCH " + tournament.getName(), 
                "[" + championName + "]", 
                "/tournament-detail/" + tName
            );
            firebaseService.sendToSubscribers(
                tName, 
                "TOURNAMENT", 
                "VUA PHÁ LƯỚI " + tournament.getName(), 
                "[" + vualuoiStr + "]", 
                "/tournament-detail/" + tName
            );
            firebaseService.sendToSubscribers(
                tName, 
                "TOURNAMENT", 
                "GĂNG TAY VÀNG " + tournament.getName(), 
                "[" + gangtayStr + "]", 
                "/tournament-detail/" + tName
            );

            List<Subscription> subs = subscriptionRepository.findByTargetIdAndTargetType(tName, "TOURNAMENT");
            for(Subscription sub : subs){
                String subName = sub.getUser().getUsername();
                sendInAppNotification(subName, "NHÀ VÔ ĐỊCH " + tournament.getName(), "[" + championName + "]", "/tournament-detail/" + tName);
                sendInAppNotification(subName, "VUA PHÁ LƯỚI " + tournament.getName(), "[" + vualuoiStr + "]", "/tournament-detail/" + tName);
                sendInAppNotification(subName, "GĂNG TAY VÀNG " + tournament.getName(), "[" + gangtayStr + "]", "/tournament-detail/" + tName);
            }
        }
    }

    @Transactional
    public void endTournament(String tName){
        Tournament tournament = tournamentRepository.findByName(tName);

        if(tournament == null){
            throw new RuntimeException("Giải đấu không tồn tại!");
        }
        
        if(!tournament.isOpening()){
            throw new RuntimeException("Giải đấu đã kết thúc!");
        }

        updateDynamicTitles(tournament);
        
        tournament.setFinishDate(LocalDateTime.now());
        tournament.setOpening(false);
        tournamentRepository.save(tournament);
    }

    @Transactional
    public void uploadLineup(String tournamentName, String username, MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new Exception("File ảnh trống!");
        }

        Tournament tournament = tournamentRepository.findByName(tournamentName);
        User user = userRepository.findByUsername(username);
        
        if (tournament == null || user == null) {
            throw new Exception("Không tìm thấy giải đấu hoặc người chơi!");
        }

        // Tìm thẻ đăng ký của người chơi này tại giải đấu
        TournamentRanking ranking = rankingRepository.findByTournamentAndUser(tournament, user);
        if (ranking == null) {
            throw new Exception("Người chơi chưa đăng ký tham gia giải đấu này!");
        }

        // 1. Tạo thư mục chứa đội hình
        String uploadDir = "uploads/lineups/";
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 2. Xóa ảnh đội hình cũ nếu Admin update lại
        if (ranking.getLineupImage() != null) {
            Path oldFilePath = uploadPath.resolve(ranking.getLineupImage());
            Files.deleteIfExists(oldFilePath);
        }

        // 3. Đặt tên file chống trùng lặp (Ví dụ: LEAGUE_Nam_b2c5d1e_anh.jpg)
        String filename = tournamentName.replaceAll("\\s+", "") + "_" + username + "_" 
                          + UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(filename);
        
        // 4. Lưu file vật lý
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 5. Cập nhật tên file vào DB
        ranking.setLineupImage(filename);
        rankingRepository.save(ranking);
    }

    // 1. TRẠM TRUNG CHUYỂN: Hàm quét DB chỉ 1 lần duy nhất
    private Map<Long, PlayerStatDTO> calculateRawStats(Tournament tournament) {
        List<Match> matches = matchRepository.findByTournament(tournament);
        Map<Long, PlayerStatDTO> statsMap = new HashMap<>();

        for (Match m : matches) {
            if (m.isUpcoming()) continue;

            if (m.getPlayer1() != null) {
                PlayerStatDTO s1 = statsMap.computeIfAbsent(m.getPlayer1().getId(), k -> new PlayerStatDTO(m.getPlayer1()));
                s1.setGoals(s1.getGoals() + m.getPlayer1Score());
                s1.setConceded(s1.getConceded() + m.getPlayer2Score());
                if (m.getPlayer2Score() == 0) s1.setCleanSheets(s1.getCleanSheets() + 1);
            }

            if (m.getPlayer2() != null) {
                PlayerStatDTO s2 = statsMap.computeIfAbsent(m.getPlayer2().getId(), k -> new PlayerStatDTO(m.getPlayer2()));
                s2.setGoals(s2.getGoals() + m.getPlayer2Score());
                s2.setConceded(s2.getConceded() + m.getPlayer1Score());
                if (m.getPlayer1Score() == 0) s2.setCleanSheets(s2.getCleanSheets() + 1);
            }
        }
        return statsMap;
    }

    // 2. HÀM CHỌC VÀO DB (Ghi đè cờ Danh hiệu)
    @Transactional
    public void updateDynamicTitles(Tournament tournament) {
        Map<Long, PlayerStatDTO> statsMap = calculateRawStats(tournament);

        int maxG = statsMap.values().stream().mapToInt(PlayerStatDTO::getGoals).max().orElse(0);
        int maxCS = statsMap.values().stream().mapToInt(PlayerStatDTO::getCleanSheets).max().orElse(0);
        int maxC = statsMap.values().stream().mapToInt(PlayerStatDTO::getConceded).max().orElse(0);

        List<TournamentRanking> rankings = rankingRepository.findByTournamentOrderByPointsDescDifferenceDescGoalsDesc(tournament);
        for (TournamentRanking r : rankings) {
            r.setTopScorer(false);
            r.setGoldenGlove(false);
            r.setMostConceded(false);

            if (r.getUser() != null && statsMap.containsKey(r.getUser().getId())) {
                PlayerStatDTO s = statsMap.get(r.getUser().getId());
                if (maxG > 0 && s.getGoals() == maxG) r.setTopScorer(true);
                if (maxCS > 0 && s.getCleanSheets() == maxCS) r.setGoldenGlove(true);
                if (maxC > 0 && s.getConceded() == maxC) r.setMostConceded(true);
            }
        }
        rankingRepository.saveAll(rankings);
    }

    // 3. HÀM ĐẨY RA GIAO DIỆN (Lấy Top 5)
    public Map<String, List<PlayerStatDTO>> getTop5Stats(Tournament tournament) {
        Map<Long, PlayerStatDTO> statsMap = calculateRawStats(tournament);
        List<PlayerStatDTO> all = new java.util.ArrayList<>(statsMap.values());
        Map<String, List<PlayerStatDTO>> res = new HashMap<>();
        
        res.put("topScorers", all.stream().filter(s -> s.getGoals() > 0)
                .sorted(java.util.Comparator.comparingInt(PlayerStatDTO::getGoals).reversed()).limit(5).collect(Collectors.toList()));

        res.put("topCleanSheets", all.stream().filter(s -> s.getCleanSheets() > 0)
                .sorted(java.util.Comparator.comparingInt(PlayerStatDTO::getCleanSheets).reversed()).limit(5).collect(Collectors.toList()));

        res.put("topConceded", all.stream().filter(s -> s.getConceded() > 0)
                .sorted(java.util.Comparator.comparingInt(PlayerStatDTO::getConceded).reversed()).limit(5).collect(Collectors.toList()));

        return res;
    }

    private void sendInAppNotification(String receiver, String title, String body, String url) {
        try {
            // 1. Lưu lịch sử vào Database để xem lại sau
            com.pesoc.website.model.InAppNotification notif = new com.pesoc.website.model.InAppNotification();
            notif.setTitle(title);
            notif.setBody(body);
            notif.setUrl(url);
            notif.setReceiverUsername(receiver);
            inAppNotificationRepository.save(notif);

            // 2. Bắn WebSocket tới kênh riêng của người nhận (nếu họ đang online)
            messagingTemplate.convertAndSend("/topic/notifications/" + receiver, notif);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
