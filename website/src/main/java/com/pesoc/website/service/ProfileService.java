package com.pesoc.website.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.UserRepository;

@Service
public class ProfileService {
    @Autowired
    private UserRepository userRepository;

    public User getProfile(String username) {
        User user = userRepository.findByUsername(username);

        if(user == null){
            throw new RuntimeException("Không tìm thấy người chơi!");
        }

        return user;
    }

    public boolean checkIfOwner(User loggedInUser, String targetUsername) {
        if (loggedInUser == null) return false;
        return loggedInUser.getUsername().equalsIgnoreCase(targetUsername);
    }
}