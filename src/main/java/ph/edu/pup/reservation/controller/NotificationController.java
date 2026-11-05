package ph.edu.pup.reservation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.pup.reservation.entity.Notification;
import ph.edu.pup.reservation.service.NotificationService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // GET /api/notifications?userId=2
    @GetMapping
    public ResponseEntity<?> getNotifications(@RequestParam Long userId) {
        List<Notification> list = notificationService.getUserNotifications(userId);
        long unread = notificationService.getUnreadCount(userId);

        List<Map<String, Object>> items = list.stream().map(n -> Map.<String, Object>of(
                "id", n.getId(),
                "title", n.getTitle(),
                "message", n.getMessage(),
                "type", n.getType() != null ? n.getType() : "INFO",
                "isRead", n.isRead(),
                "time", n.getCreatedAt() != null ? n.getCreatedAt().toString().replace('T', ' ').substring(0, 16) : ""
        )).toList();

        return ResponseEntity.ok(Map.of("unreadCount", unread, "notifications", items));
    }

    // POST /api/notifications/{id}/read
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // POST /api/notifications/read-all?userId=2
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllRead(@RequestParam Long userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}