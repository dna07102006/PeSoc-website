package com.pesoc.website.model;

import java.time.LocalDateTime;
import jakarta.persistence.*; 
import lombok.Data;

@Data
@Entity
@Table(name = "matches")
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player1_id") 
    private User player1;

    @ManyToOne
    @JoinColumn(name = "player2_id")
    private User player2;

    private Integer player1Score;
    private Integer player2Score;
    private Integer player1Elo;
    private Integer player2Elo;
    private Integer player1Pen; 
    private Integer player2Pen; 
    private LocalDateTime date;
    private boolean upcoming;
    
    @ManyToOne
    @JoinColumn(name = "tournament_id") 
    private Tournament tournament;

    private String matchType;
    private String phaseName;
    private Integer roundNumber;
}
