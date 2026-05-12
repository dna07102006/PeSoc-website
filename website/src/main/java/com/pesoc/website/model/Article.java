package com.pesoc.website.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Entity
@Table(name = "articles")
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;       
    
    @Column(length = 500)
    private String summary;     

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;     

    private String thumbnail;   
    private LocalDateTime createdAt;
    
    @ManyToOne
    @JoinColumn(name = "author_id")
    @JsonIgnore
    private User author;   
    
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Comment> comments;

    // Trong Article.java thêm:
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();
}