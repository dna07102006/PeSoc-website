package com.pesoc.website.configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.pesoc.website.model.EloHistory;
import com.pesoc.website.model.User;
import com.pesoc.website.repository.UserRepository;

@Component
public class DataInitializer implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // Chỉ chèn nếu Database chưa có ai
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername("Nam");
            admin.setPassword("123");
            admin.setRole("ADMIN");
            admin.setElo(1300);

            // 1. Khởi tạo danh sách lịch sử cho User
            admin.setEloHistories(new ArrayList<>());

            // 2. Tạo mốc lịch sử đầu tiên
            EloHistory initHistory = new EloHistory();
            initHistory.setUser(admin); // Gắn user vào lịch sử
            initHistory.setElo(1300);
            initHistory.setChangeDate(LocalDateTime.now());
            initHistory.setNote("Khởi tạo tài khoản Admin!");

            // 3. Nhét lịch sử vào danh sách của User
            admin.getEloHistories().add(initHistory);

            // 4. Lưu User (nhờ CascadeType.ALL nên EloHistory cũng sẽ tự được lưu)
            userRepository.save(admin);
            
            System.out.println(">>> Đã khởi tạo tài khoản Nam (Admin) kèm mốc Elo lịch sử!");
        }
    }
}