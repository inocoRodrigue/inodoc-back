package com.esn.inodoc.repo.cra;
import com.esn.inodoc.domain.cra.CraReminder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
public interface CraReminderRepository extends JpaRepository<CraReminder, UUID> {
    boolean existsByUserIdAndYearAndMonthAndQueuedDate(UUID userId, int year, int month, LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CraReminder> findTop20ByStateAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(String state, Instant now);
    Page<CraReminder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}