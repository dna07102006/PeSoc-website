package com.pesoc.website.repository;

import com.pesoc.website.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.*;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByUsername(String username);

    List<User> findTop5ByOrderByEloDesc();

    List<User> findAll();

    List<User> findAllByOrderByEloDesc();

    List<User> findAllByOrderByEloAsc();

    List<User> findByUsernameContainingIgnoreCaseOrPesUsernameContainingIgnoreCase(String username, String pesUsername);

    @Query("SELECT u FROM User u WHERE " +"LOWER(u.username) LIKE LOWER(CONCAT('%', :kw, '%')) OR " + "LOWER(u.pesUsername) LIKE LOWER(CONCAT('%', :kw, '%'))")
    List<User> searchUsers(@Param("kw") String kw);
}