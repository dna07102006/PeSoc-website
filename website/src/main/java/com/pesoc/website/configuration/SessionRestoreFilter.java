package com.pesoc.website.configuration;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.pesoc.website.model.User;
import com.pesoc.website.repository.UserRepository;
import java.io.IOException;

@Component
public class SessionRestoreFilter implements Filter {

    @Autowired
    private UserRepository userRepository; // Đổi lại thành service/repo của bạn

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession();

        // 1. Nếu Session bị mất trí nhớ (bị null do tắt trình duyệt)
        if (session.getAttribute("logInUser") == null) {
            
            // 2. Chặn khách lại, lục soát xem có mang cái Cookie "peSoc_remember" không
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("peSoc_remember".equals(cookie.getName())) {
                        
                        // 3. Khách có mang thẻ VIP! Đọc tên username từ thẻ
                        String savedUsername = cookie.getValue();
                        
                        // 4. Lấy từ Database ra và bơm lại vào Session
                        User currentUser = userRepository.findByUsername(savedUsername);
                        if (currentUser != null) {
                            session.setAttribute("logInUser", currentUser);
                        }
                        break; // Tìm thấy rồi thì thoát vòng lặp
                    }
                }
            }
        }

        // 5. Mời khách đi tiếp vào web bình thường
        chain.doFilter(request, response);
    }
}