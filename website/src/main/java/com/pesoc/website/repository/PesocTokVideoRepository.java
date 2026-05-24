package com.pesoc.website.repository;

import com.pesoc.website.model.PesocTokVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PesocTokVideoRepository extends JpaRepository<PesocTokVideo, Long> {
    List<PesocTokVideo> findAllByOrderByCreatedAtDesc();
}
