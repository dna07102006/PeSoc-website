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
// 🌟 THÊM 2 DÒNG IMPORT THƯ VIỆN GIẢI MÃ NÀY SẾP NHÉ
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class SessionRestoreFilter implements Filter {

    @Autowired
    private UserRepository userRepository; 

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
                        
                        try {
                            // 3. 🌟 BÍ QUYẾT CHỮA BỆNH: Giải mã chuỗi Cookie hoàn nguyên về chữ tiếng Việt có dấu
                            // (Ví dụ: Đổi từ "Hoa%CC%80ng%20%Đu%CC%81c" quay lại thành "Hoàng Đức" xịn xò)
                            String savedUsername = URLDecoder.decode(cookie.getValue(), StandardCharsets.UTF_8);
                            
                            // 4. Lấy từ Database ra và bơm lại vào Session chuẩn đét 100%
                            User currentUser = userRepository.findByUsername(savedUsername);
                            if (currentUser != null) {
                                session.setAttribute("logInUser", currentUser);
                            }
                        } catch (Exception e) {
                            System.out.println("❌ Lỗi giải mã Cookie tự động login: " + e.getMessage());
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