package com.pesoc.website.service;

import com.pesoc.website.model.User;
import com.pesoc.website.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);

        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }

    public void registerNewPlayer(User user) {
        user.setRole("PLAYER");
        userRepository.save(user); 
    }
}