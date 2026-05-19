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
    
    // 1. Lấy URL từ thông báo (fallback về /home nếu không tìm thấy)
    const targetUrl = (event.notification.data && event.notification.data.url) 
                      ? event.notification.data.url 
                      : '/home';

    // 2. Chuyển đổi URL sang dạng đầy đủ
    const fullUrl = new URL(targetUrl, self.location.origin).href;

    event.waitUntil(
        clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function(clientList) {
            // Tìm xem có cửa sổ nào của PéSoc đang mở không
            for (var i = 0; i < clientList.length; i++) {
                var client = clientList[i];
                // Nếu tìm thấy cửa sổ đang mở -> Focus vào nó VÀ ép nó nhảy sang URL mới
                if (client.url && 'focus' in client) {
                    return client.focus().then(function(focusedClient) {
                        return focusedClient.navigate(fullUrl);
                    });
                }
            }
            // Nếu không tìm thấy cửa sổ nào (App đang tắt) -> Mở mới
            if (clients.openWindow) {
                return clients.openWindow(fullUrl);
            }
        })
    );
});