importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-messaging-compat.js');

const firebaseConfig = {
    apiKey: "AIzaSyCR8LKoUZ7JY7hPf7LQsVwF1mS-iCsZd98",
    authDomain: "pesoc-e16d0.firebaseapp.com",
    projectId: "pesoc-e16d0",
    storageBucket: "pesoc-e16d0.firebasestorage.app",
    messagingSenderId: "1009827135409",
    appId: "1:1009827135409:web:5ed31c94785e71b4438790",
    measurementId: "G-4720672Q56"
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

self.addEventListener('notificationclick', function(event) {
    event.notification.close();

    // 1. Trích xuất URL mục tiêu (Chống crash Safari)
    let targetUrl = '/home';
    if (event.notification.data) {
        if (event.notification.data.url) { targetUrl = event.notification.data.url; }
        else if (event.notification.data.link) { targetUrl = event.notification.data.link; }
        else if (event.notification.data.FCM_MSG && event.notification.data.FCM_MSG.data && event.notification.data.FCM_MSG.data.url) {
            targetUrl = event.notification.data.FCM_MSG.data.url;
        }
    }

    // 2. Tạo 2 đường link chuẩn bị sẵn
    let fullTargetUrl = new URL(targetUrl, self.location.origin).href;
    let homeUrl = new URL('/home', self.location.origin).href;

    // 3. CHIẾN THUẬT RẼ NHÁNH (HYBRID NAVIGATION)
    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            
            // TRƯỜNG HỢP A: 🟢 APP ĐANG MỞ (Hoặc đang chạy ngầm)
            if (clientList.length > 0) {
                let client = clientList[0];
                return client.focus().then(function(focusedClient) {
                    // Vì app đang mở, trình duyệt đủ sức nhảy vào thẳng link chi tiết!
                    if (focusedClient) { return focusedClient.navigate(fullTargetUrl); } 
                    else { return client.navigate(fullTargetUrl); }
                });
            } 
            
            // TRƯỜNG HỢP B: 🔴 APP BỊ TẮT HOÀN TOÀN (KILLED)
            else {
                // Nhảy URL sâu hay bị lỗi trên điện thoại, ta ép mở App ở trang Home cho an toàn
                if (clients.openWindow) {
                    return clients.openWindow(homeUrl);
                }
            }
        })
    );
});