package com.pesoc.website.model;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.*; 
import lombok.Data;

@Data
@Entity
@Table(name = "tournaments")
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL)
    private List<Match> matches;

    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL)
    private List<TournamentRanking> ranking;

    private LocalDateTime openDate;
    private LocalDateTime finishDate;

    private String tournamentType;
    private boolean opening;

    // Thêm trường này vào class Tournament
    private String banner;

}
