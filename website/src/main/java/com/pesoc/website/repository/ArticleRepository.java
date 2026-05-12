package com.pesoc.website.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.pesoc.website.model.Article;
import com.pesoc.website.model.User;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findAllByOrderByCreatedAtDesc();

    List<Article> findByTags_Name(String tagName);

    List<Article> findTop5ByOrderByCreatedAtDesc();

    Page<Article> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Article> findByTags_Name(String tagName, Pageable pageable);

    Page<Article> findByAuthor(User author, Pageable pageable);
    // Lấy 5 bài mới nhất nhưng loại trừ bài đang xem
    List<Article> findTop5ByIdNotOrderByCreatedAtDesc(Long id);
}
