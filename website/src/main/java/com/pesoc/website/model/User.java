package com.pesoc.website.model;

import java.util.List;
import jakarta.persistence.*; 
import lombok.Data;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String pesUsername;
    private String password;
    private Integer elo; 

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<EloHistory> eloHistories;
    
    private String role;

    private String description;

    private String avatar;
}