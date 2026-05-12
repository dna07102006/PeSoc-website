package com.pesoc.website.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.pesoc.website.model.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

}