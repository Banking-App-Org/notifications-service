package notifications.microservice.repository;

import notifications.microservice.entity.NotificationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Long> {
    List<NotificationEvent> findByMemberId(String memberId);

    List<NotificationEvent> findByStatus(String status);

    @Query("SELECT ne FROM NotificationEvent ne WHERE ne.createdAt >= ?1 AND ne.createdAt <= ?2")
    List<NotificationEvent> findByDateRange(LocalDateTime start, LocalDateTime end);

    @Query("SELECT ne FROM NotificationEvent ne WHERE ne.status = 'FAILED' OR (ne.status = 'RETRYING' AND ne.retryCount < 3)")
    List<NotificationEvent> findFailedAndRetryingEvents();

    List<NotificationEvent> findByMemberIdAndEventType(String memberId, String eventType);

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationEvent ne WHERE ne.metadata LIKE '%\"loadTest\":true%'")
    int deleteLoadTestEvents();

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationEvent ne WHERE ne.memberId LIKE 'lt-user-%'")
    int deleteLoadTestEventsByMemberId();

    @Query("SELECT e FROM NotificationEvent e WHERE e.resent = false OR e.status = 'FAILED' ORDER BY e.id ASC")
    List<NotificationEvent> findUnresent(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEvent e SET e.resent = true WHERE e.id IN :ids")
    void markAsResent(@Param("ids") List<Long> ids);
}

