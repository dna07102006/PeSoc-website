package com.pesoc.website.service;

import java.time.LocalDateTime;
import java.util.*;
import java.lang.Math;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pesoc.website.model.*;
import com.pesoc.website.repository.*;

@Transactional
@Service
public class AdminService {
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

    public Pair<Integer, Integer> updateElo(Integer elo1, Integer elo2, String res, Integer kFactor, int pen){
        double Qa = Math.pow(10, elo1 / 400.0);
        double Qb = Math.pow(10, elo2 / 400.0);
        double Ea = Qa / (Qa + Qb);
        double Eb = Qb / (Qa + Qb);
        double Aa, Ab;
        if(pen == 0){
            if("thắng".equals(res)){
                Aa = 1;
                Ab = 0;
            }
            else if("hòa".equals(res)){
                Aa = 0.5;
                Ab = 0.5;
            }
            else{
                Aa = 0;
                Ab = 1;
            }
        }
        else{
            if("thắng".equals(res)){
                Aa = 0.75;
                Ab = 0.5;
            }
            else if("hòa".equals(res)){
                Aa = 0.5;
                Ab = 0.5;
            }
            else{
                Aa = 0.5;
                Ab = 0.75;
            }
        }
        
        double Ka, Kb;
        if(elo1 < 1200){
            Ka = 40;
        }
        else if(elo1 < 1500){
            Ka = 25;
        }
        else if(elo1 < 1800){
            Ka = 15;
        }
        else{
            Ka = 10;
        }
        if(elo2 < 1200){
            Kb = 40;
        }
        else if(elo2 < 1500){
            Kb = 25;
        }
        else if(elo2 < 1800){
            Kb = 15;
        }
        else{
            Kb = 10;
        }

        int elo1After = (int) Math.round(elo1 + Ka * kFactor * (Aa - Ea));
        int elo2After = (int) Math.round(elo2 + Kb * kFactor * (Ab - Eb));

        return Pair.of(elo1After, elo2After);
    }

    private void updateTournamentRanking(Match match, int s1, int s2) {
        TournamentRanking rank1 = getOrCreateRanking(match.getTournament(), match.getPlayer1());
        TournamentRanking rank2 = getOrCreateRanking(match.getTournament(), match.getPlayer2());

        rank1.setMatchesPlayed(rank1.getMatchesPlayed() + 1);
        rank2.setMatchesPlayed(rank2.getMatchesPlayed() + 1);

        rank1.setGoals(rank1.getGoals() + s1);
        rank2.setGoals(rank2.getGoals() + s2);

        rank1.setConceded(rank1.getConceded() + s2);
        rank2.setConceded(rank2.getConceded() + s1);

        rank1.setDifference(rank1.getDifference() + s1 - s2);
        rank2.setDifference(rank2.getDifference() + s2 - s1);

        if (s1 > s2) {
            rank1.setWins(rank1.getWins() + 1);
            rank1.setPoints(rank1.getPoints() + 3);
            rank2.setLosses(rank2.getLosses() + 1);
        } else if (s1 < s2) {
            rank2.setWins(rank2.getWins() + 1);
            rank2.setPoints(rank2.getPoints() + 3);
            rank1.setLosses(rank1.getLosses() + 1);
        } else {
            rank1.setDraws(rank1.getDraws() + 1);
            rank1.setPoints(rank1.getPoints() + 1);
            rank2.setDraws(rank2.getDraws() + 1);
            rank2.setPoints(rank2.getPoints() + 1);
        }

        rankingRepository.save(rank1);
        rankingRepository.save(rank2);
    }

    private TournamentRanking getOrCreateRanking(Tournament t, User u) {
        TournamentRanking tournamentRanking = rankingRepository.findByTournamentAndUser(t, u);

        if(tournamentRanking == null){
            tournamentRanking = new TournamentRanking();
            tournamentRanking.setTournament(t);
            tournamentRanking.setUser(u);
        }

        return tournamentRanking;
    }

    @Transactional
    public void completeMatch(Long MatchID, Integer score1, Integer score2, Integer pen1, Integer pen2){
        Match match = matchRepository.findById(MatchID).orElseThrow(() -> new RuntimeException("Không tìm thấy trận đấu!"));

        match.setPlayer1Elo(match.getPlayer1().getElo());
        match.setPlayer2Elo(match.getPlayer2().getElo());
        match.setPlayer1Score(score1);
        match.setPlayer2Score(score2);
        match.setPlayer1Pen(pen1);
        match.setPlayer2Pen(pen2);
        match.setUpcoming(false);
        match.setDate(LocalDateTime.now());
        matchRepository.save(match);

        int pen = 0;

        String res1, res2;
        if(score1 > score2){
            res1 = "thắng";
            res2 = "thua";
        }
        else if(score1 < score2){
            res1 = "thua";
            res2 = "thắng";
        }
        else {
            if (pen1 != null && pen2 != null && !pen1.equals(pen2)) {
                pen = 1;
                if (pen1 > pen2) {
                    res1 = "thắng";
                    res2 = "thua";
                } else {
                    res1 = "thua";
                    res2 = "thắng";
                }
            } else {
                res1 = "hòa";
                res2 = "hòa";
            }
        }

        Integer kFactor = 1;
        if(match.getTournament() == null){
            kFactor = 1;
        }

        if(match.getTournament() != null){
            kFactor = 2;

            if("GROUP".equals(match.getMatchType())){
                updateTournamentRanking(match, score1, score2); 
            }

            // ==========================================
            // 🌟 QUÉT CHO MỌI VÒNG ĐẤU (Group, Tứ kết, Bán kết...)
            // ==========================================
            String currentPhase = match.getPhaseName();
            boolean isPhaseFinished = matchRepository.findByTournament(match.getTournament())
                .stream()
                // Lọc tất cả các trận có CÙNG TÊN VÒNG (ví dụ: cùng là "Tứ kết")
                .filter(m -> currentPhase != null && currentPhase.equals(m.getPhaseName()))
                // Kiểm tra xem tất cả các trận đó đã đá xong hết chưa
                .allMatch(m -> !m.isUpcoming()); 
            
            if (isPhaseFinished) {
                firebaseService.sendToSubscribers(
                    match.getTournament().getName(),
                    "TOURNAMENT",
                    currentPhase + " đã khép lại! 🏁",
                    "Bạn có cuộc gọi nhỡ từ " + match.getTournament().getName() + ": vừa hoàn tất các trận đấu của " + currentPhase + "!",
                    "/tournament-detail/" + match.getTournament().getName()
                );

                List<Subscription> subs = subscriptionRepository.findByTargetIdAndTargetType(match.getTournament().getName(), "TOURNAMENT");
                for(Subscription sub : subs){
                    String subName = sub.getUser().getUsername();
                    sendInAppNotification(subName, currentPhase + " đã khép lại! 🏁", "Bạn có cuộc gọi nhỡ từ " + match.getTournament().getName() + ": vừa hoàn tất các trận đấu của " + currentPhase + "!", "/tournament-detail/" + match.getTournament().getName());
                }
            }
        }

        var eloRes = updateElo(match.getPlayer1().getElo(), match.getPlayer2().getElo(), res1, kFactor, pen);
        Integer elo1After = eloRes.getFirst();
        Integer elo2After = eloRes.getSecond();
        match.getPlayer1().setElo(elo1After);
        match.getPlayer2().setElo(elo2After);

        String penNote = (pen1 != null && pen2 != null) ? " (Pen: " + pen1 + "-" + pen2 + ")" : "";

        EloHistory h1 = new EloHistory();
        h1.setUser(match.getPlayer1());
        h1.setElo(elo1After);
        h1.setChangeDate(LocalDateTime.now());
        if(match.getTournament() == null){
            h1.setNote("Trận " + res1 + " giao hữu với " + match.getPlayer2().getUsername() + " (" + match.getPlayer2Elo() + ")" + penNote + ".");
        }
        else{
            h1.setNote("Trận " + res1 + " tại " + match.getTournament().getName() + " với " + match.getPlayer2().getUsername() + " (" + match.getPlayer2Elo() + ")" + penNote + ".");
        }

        EloHistory h2 = new EloHistory();
        h2.setUser(match.getPlayer2());
        h2.setElo(elo2After);
        h2.setChangeDate(LocalDateTime.now());
        if(match.getTournament() == null){
            h2.setNote("Trận " + res2 + " giao hữu với " + match.getPlayer1().getUsername() + " (" + match.getPlayer1Elo() + ")" + penNote + ".");
        }
        else{
            h2.setNote("Trận " + res2 + " tại " + match.getTournament().getName() + " với " + match.getPlayer1().getUsername() + " (" + match.getPlayer1Elo() + ")" + penNote + ".");
        }

        if (match.getPlayer1().getEloHistories() == null) {
            match.getPlayer1().setEloHistories(new ArrayList<>());
        }
        if (match.getPlayer2().getEloHistories() == null) {
            match.getPlayer2().setEloHistories(new ArrayList<>());
        }

        match.getPlayer1().getEloHistories().add(h1);
        match.getPlayer2().getEloHistories().add(h2);

        userRepository.save(match.getPlayer1());
        userRepository.save(match.getPlayer2());

        // --- ĐOẠN CODE MỚI THAY THẾ: GỘP FAN BẮN 1 PHÁT DUY NHẤT ---
        String p1Name = match.getPlayer1().getUsername(); //
        String p2Name = match.getPlayer2().getUsername(); //
        String matchUrl = "/match-detail/" + MatchID; //
        String msgBody = p1Name + " " + score1 + " - " + score2 + " " + p2Name; //

        // Gọi hàm thông minh để lọc trùng fan trước khi bắn tin
        firebaseService.sendToMatchPlayersSubscribers(
            p1Name, 
            p2Name, 
            "Full time! ⚽", 
            msgBody, 
            matchUrl
        );

        List<Subscription> sub1s = subscriptionRepository.findByTargetIdAndTargetType(p1Name, "PLAYER");
        List<Subscription> sub2s = subscriptionRepository.findByTargetIdAndTargetType(p2Name, "PLAYER");

        Set<String> subs = new HashSet<String>();
        for(Subscription sub1 : sub1s){
            subs.add(sub1.getUser().getUsername());
        }
        for(Subscription sub2 : sub2s){
            subs.add(sub2.getUser().getUsername());
        }
        for(String sub : subs){
            sendInAppNotification(sub, "Full time! ⚽", msgBody, matchUrl);
        }
    }

    public void createMatch(String player1, String player2){
        User user1 = userRepository.findByUsername(player1);
        User user2 = userRepository.findByUsername(player2);

        if(user1 == null || user2 == null){
            throw new RuntimeException("Một trong hai người chơi không tồn tại!");
        }
        if(user1 == user2){
            throw new RuntimeException("Người chơi không thể tự đấu với mình!");
        }

        Match match = new Match();
        match.setPlayer1(user1);
        match.setPlayer2(user2);
        match.setUpcoming(true);
        matchRepository.save(match);
    }

    @Transactional
    public void addPlayer(String username, Integer initialElo){
        User user = userRepository.findByUsername(username);

        if(user != null){
            throw new RuntimeException("Người chơi đã tồn tại!");
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword("123");
        newUser.setElo(initialElo);
        newUser.setRole("PLAYER");

        if (newUser.getEloHistories() == null) {
            newUser.setEloHistories(new ArrayList<>());
        }

        EloHistory eloHistory = new EloHistory();
        eloHistory.setUser(newUser);
        eloHistory.setElo(initialElo);
        eloHistory.setChangeDate(LocalDateTime.now());
        eloHistory.setNote("Khởi tạo account!");
        newUser.getEloHistories().add(eloHistory);

        userRepository.save(newUser);
    }

    public void createTournament(String name, String Type){
        Tournament tournament = tournamentRepository.findByName(name);

        if(tournament != null){
            throw new RuntimeException("Giải đấu đã tồn tại!");
        }

        tournament = new Tournament();
        tournament.setName(name);
        tournament.setTournamentType(Type);
        tournament.setOpening(true);
        tournament.setOpenDate(LocalDateTime.now());
        tournamentRepository.save(tournament);
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