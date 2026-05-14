package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class TournamentRanking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    private String groupName;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private int points = 0;
    private int matchesPlayed = 0;
    private int wins = 0;
    private int draws = 0;
    private int losses = 0;
    private int goals = 0;
    private int conceded = 0;
    private int difference = 0;
    private Integer finalRank;

    private String lineupImage;

    @Column(columnDefinition = "boolean default false")
    private boolean topScorer = false;

    @Column(columnDefinition = "boolean default false")
    private boolean goldenGlove = false;

    @Column(columnDefinition = "boolean default false")
    private boolean mostConceded = false;
}