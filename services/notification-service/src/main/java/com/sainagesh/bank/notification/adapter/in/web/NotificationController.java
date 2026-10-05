package com.sainagesh.bank.notification.adapter.in.web;

import com.sainagesh.bank.notification.application.NotificationQueries;
import com.sainagesh.bank.notification.domain.Channel;
import com.sainagesh.bank.notification.domain.Notification;
import com.sainagesh.bank.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The messages sent about an account, for the app's inbox. */
@RestController
@RequestMapping("/v1/notifications")
class NotificationController {

    private final NotificationQueries queries;

    NotificationController(NotificationQueries queries) {
        this.queries = queries;
    }

    record NotificationResponse(UUID id, UUID accountId, String eventType, Channel channel, String title, String body, Instant createdAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.id(),
                    notification.accountId(),
                    notification.eventType(),
                    notification.channel(),
                    notification.title(),
                    notification.body(),
                    notification.createdAt());
        }
    }

    @GetMapping
    List<NotificationResponse> list(@RequestParam UUID accountId, @RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 200) {
            throw ApiException.badRequest("INVALID_PARAMETER", "limit must be from 1 to 200");
        }
        return queries.latestFor(accountId, limit).stream().map(NotificationResponse::from).toList();
    }
}
