package com.pesoc.website.configuration;

import org.springframework.stereotype.*;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import com.pesoc.website.model.User;
import jakarta.servlet.http.*;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        User user = (User) request.getSession().getAttribute("logInUser");

        if (user == null) {
            FlashMap flashMap = new FlashMap();
            flashMap.put("message", "Vui lòng đăng nhập để tiếp tục!");
            
            FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
            
            response.sendRedirect("/");
            return false;
        }

        if (request.getRequestURI().startsWith("/admin") && !"ADMIN".equals(user.getRole())) {
            FlashMap flashMap = new FlashMap();
            flashMap.put("message", "Bạn không có quyền truy cập khu vực Admin!");
            
            FlashMapManager flashMapManager = RequestContextUtils.getFlashMapManager(request);
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
            
            response.sendRedirect("/");
            return false;
        }

        return true;
    }
}