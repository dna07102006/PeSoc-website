package com.pesoc.website.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.UserRepository;

@Service
public class AuthService {
    @Autowired
    private UserRepository userRepository;

    public User authenticate(String username, String password) {
        User user = userRepository.findByUsername(username);
    
        if (user != null && password != null && password.equals(user.getPassword())) {
            return user; 
        }
        
        return null;
    }
}