package com.pesoc.website.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.Cookie; // Nhớ import thư viện Cookie
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse; // Cần cái này để gửi/xóa Cookie
import jakarta.servlet.http.HttpSession;
import com.pesoc.website.model.User;
import com.pesoc.website.service.AuthService;

@Controller
public class AuthController {
    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public String logIn(
            @RequestParam("username") String username, 
            @RequestParam("password") String password, 
            @RequestParam(value = "remember-me", required = false) String rememberMe, // Hứng nút tick từ HTML
            HttpSession session, 
            RedirectAttributes ra, 
            HttpServletRequest request,
            HttpServletResponse response){ // Thêm response để gửi Cookie về
            
        String referer = request.getHeader("Referer");
        User user = authService.authenticate(username, password); 
        
        if(user != null){
            session.setAttribute("logInUser", user);
            
            // 🌟 PHÁT THẺ VIP NẾU CÓ TICK "GHI NHỚ" 🌟
            if (rememberMe != null) {
                Cookie cookie = new Cookie("peSoc_remember", user.getUsername());
                cookie.setMaxAge(7 * 24 * 60 * 60); // Sống 7 ngày (tính bằng giây)
                cookie.setPath("/"); // Có tác dụng trên mọi trang của PeSoc
                response.addCookie(cookie); // Nhét vào túi người dùng
            }
            
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
    public String logOut(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        // 1. Xóa túi đồ trong RAM
        session.invalidate();
        
        // 2. 🌟 TỊCH THU LẠI THẺ VIP (COOKIE) NẾU CÓ 🌟
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("peSoc_remember".equals(cookie.getName())) {
                    cookie.setMaxAge(0); // Bí quyết xóa Cookie: Ép tuổi thọ về 0 giây
                    cookie.setPath("/"); // Phải khớp với cái đường dẫn lúc tạo
                    response.addCookie(cookie);
                    break;
                }
            }
        }
        
        return "redirect:/"; 
    }
}