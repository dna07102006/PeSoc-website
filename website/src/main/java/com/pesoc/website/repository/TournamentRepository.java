package com.pesoc.website.repository;

import com.pesoc.website.model.Tournament;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.*;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findAll();

    Tournament findByName(String name);

    List<Tournament> findByOpeningTrue();

    List<Tournament> findByNameContainingIgnoreCase(String name);

    @Query("SELECT t FROM Tournament t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<Tournament> searchTournaments(@Param("kw") String kw);

    List<Tournament> findByOpeningOrderByOpenDateDesc(boolean opening);

    // Lấy danh sách giải ĐÃ kết thúc và phân trang (dạng Page)
    Page<Tournament> findByOpeningOrderByFinishDateDesc(boolean opening, Pageable pageable);
}