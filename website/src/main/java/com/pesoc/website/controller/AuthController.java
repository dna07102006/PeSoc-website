package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.pesoc.website.model.User;
import com.pesoc.website.service.AuthService;

@Controller
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public String logIn(@RequestParam("username") String username, @RequestParam("password") String password, HttpSession session, RedirectAttributes ra, HttpServletRequest request){
        String referer = request.getHeader("Referer");
        User user = authService.authenticate(username, password); 
        if(user != null){
            session.setAttribute("logInUser", user);
            if("ADMIN".equals(user.getRole())){
                ra.addAttribute("message", "Chào mừng Admin!");
                return "redirect:" + referer;
            }
            ra.addAttribute("message", "Đăng nhập thành công!");
            return "redirect:" + referer;
        }

        ra.addAttribute("message", "Lỗi: Sai tên đăng nhập!");
        return "redirect:" + referer;
    }

    @GetMapping("/logout")
        public String logOut(HttpSession session) {
            session.invalidate();
            return "redirect:/"; 
        }
}