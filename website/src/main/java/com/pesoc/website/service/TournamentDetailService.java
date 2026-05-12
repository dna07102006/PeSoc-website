package com.pesoc.website.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pesoc.website.model.Match;
import com.pesoc.website.model.Tournament;
import com.pesoc.website.model.TournamentRanking;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.MatchRepository;
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

        Match match = new Match();
        match.setTournament(tournament);
        match.setPlayer1(user1);
        match.setPlayer2(user2);
        match.setUpcoming(true);
        
        match.setMatchType(matchType);
        
        if("GROUP".equals(matchType) && phaseName.isEmpty()){
            match.setPhaseName("Vòng " + roundNumber);
        } 
        else{
            match.setPhaseName(phaseName); 
        }
        
        match.setRoundNumber(roundNumber);

        matchRepository.save(match);
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
        TournamentRanking ranking = rankingRepository.findById(rankingId).orElseThrow(() -> new RuntimeException("Không tìm thấy dữ liệu xếp hạng!"));
        
        ranking.setFinalRank(rank);
        rankingRepository.save(ranking);
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
}
