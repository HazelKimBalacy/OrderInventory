package edu.cit.balacy.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    void record(NotificationType type, String message) {
        repository.save(new Notification(type, message));
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getLog() {
        return repository.findAllByOrderByNotificationIdDesc().stream()
                .map(NotificationDTO::from)
                .toList();
    }
}
