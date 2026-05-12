package com.pesoc.website.model;

import jakarta.persistence.Entity;
import java.util.List;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Tag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String name; // Ví dụ: "Tactics", "Highlight", "Tournament"

    @ManyToMany(mappedBy = "tags")
    private List<Article> articles;
}