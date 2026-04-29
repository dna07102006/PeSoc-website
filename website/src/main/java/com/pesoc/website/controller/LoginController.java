package com.pesoc.website.controller;

import com.pesoc.website.model.User;
import com.pesoc.website.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class LoginController {
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public String handleLogin(@RequestParam String username, 
                             @RequestParam String password, 
                             HttpSession session) {
        User user = userService.login(username, password);
        
        if (user != null){
            session.setAttribute("loggedInUser", user);
            
            if("ADMIN".equals(user.getRole())){
                return "redirect:/admin/dashboard";
            } 
            else{
                return "redirect:/player/home";
            }
        }
        return "login?error=true";
    }

    // Hiển thị trang login khi gõ localhost:8080/login
    @GetMapping("/login")
    public String showLoginPage() {
        return "login"; // Nó sẽ tự tìm file login.html trong templates
    }

    // Trang dashboard của Admin
    @GetMapping("/admin/dashboard")
    public String adminDashboard(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        // Kiểm tra bảo mật cơ bản dựa trên Role [cite: 255, 283]
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return "redirect:/login";
        }
        return "admin/dashboard";
    }

    @GetMapping("/register")
    public String showRegisterPage() {
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@ModelAttribute User user) {
        // Kiểm tra xem User đã tồn tại chưa bằng hàm equals() chúng ta đã viết [cite: 443-450]
        // Ở đây tạm thời cứ lưu luôn cho nhanh b nhé
        userService.registerNewPlayer(user);
        return "redirect:/login?success=true";
    }
}