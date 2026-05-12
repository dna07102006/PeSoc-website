package com.pesoc.website.repository;

import com.pesoc.website.model.Match;
import com.pesoc.website.model.Tournament;
import com.pesoc.website.model.User;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.*;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAll();
    
    List<Match> findByUpcoming(Boolean upcoming);

    List<Match> findTop5ByUpcomingOrderByDateDesc(Boolean upcoming);

    List<Match> findByTournamentOrderByDateDesc(Tournament tournament);

    List<Match> findByTournamentAndMatchTypeOrderByRoundNumberAscDateDesc(Tournament tournament, String matchType);

    @Query("SELECT m FROM Match m WHERE m.player1 = :user OR m.player2 = :user ORDER BY m.date DESC")
    List<Match> findAllByPlayer(@Param("user") User user);

    List<Match> findTop5ByUpcomingOrderByDateAsc(boolean upcoming);

    List<Match> findByUpcomingOrderByIdAsc(boolean upcoming);

    List<Match> findByUpcomingOrderByDateDesc(boolean upcoming);

    // Tìm tất cả các trận giữa 2 người chơi (bất kể ai là player1 hay player2)
    @Query("SELECT m FROM Match m WHERE (m.player1.id = :p1 AND m.player2.id = :p2) OR (m.player1.id = :p2 AND m.player2.id = :p1) ORDER BY m.date DESC")
    List<Match> findHeadToHead(@Param("p1") Long p1, @Param("p2") Long p2);

    Page<Match> findByUpcomingOrderByDateDesc(boolean upcoming, Pageable pageable);
    Page<Match> findByUpcomingOrderByIdAsc(boolean upcoming, Pageable pageable);
}