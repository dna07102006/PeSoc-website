package com.pesoc.website.repository;

import com.pesoc.website.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.*;

@Repository
public interface TournamentRankingRepository extends JpaRepository<TournamentRanking, Long> {
    List<TournamentRanking> findByTournamentOrderByPointsDescDifferenceDescGoalsDesc(Tournament tournament);

    TournamentRanking findByTournamentAndUser(Tournament tournament, User user);

    List<TournamentRanking> findByUser(User user);
}