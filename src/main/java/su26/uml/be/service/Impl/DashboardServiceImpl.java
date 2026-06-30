package su26.uml.be.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.DashboardStatResponse;
import su26.uml.be.enums.UserStatus;
import su26.uml.be.repository.ProjectRepository;
import su26.uml.be.repository.SheetRepository;
import su26.uml.be.repository.UserRepository;
import su26.uml.be.service.DashboardService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DashboardServiceImpl implements DashboardService {
    UserRepository userRepository;
    ProjectRepository projectRepository;
    SheetRepository sheetRepository;

    @Override
    public ApiResponse<DashboardStatResponse> getUserStats(String range, LocalDate from, LocalDate to) {
        return buildStat(range, from, to,
                (f, t) -> userRepository.countByCreatedAtBetweenAndStatus(f, t, UserStatus.ACTIVE));
    }

    @Override
    public ApiResponse<DashboardStatResponse> getProjectStats(String range, LocalDate from, LocalDate to) {
        return buildStat(range, from, to,
                projectRepository::countByCreatedAtBetweenAndIsDeletedFalse);
    }

    @Override
    public ApiResponse<DashboardStatResponse> getDiagramStats(String range, LocalDate from, LocalDate to) {
        return buildStat(range, from, to,
                sheetRepository::countByCreatedAtBetween);
    }

    private ApiResponse<DashboardStatResponse> buildStat(
            String range, LocalDate from, LocalDate to,
            BiFunction<LocalDateTime, LocalDateTime, Long> counter) {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeFrom;
        LocalDateTime rangeTo;

        switch (range) {
            case "24h" -> {
                rangeTo = now;
                rangeFrom = now.minusHours(24);
            }
            case "7d" -> {
                rangeTo = now;
                rangeFrom = now.minusDays(7);
            }
            case "custom" -> {
                if (from == null || to == null) {
                    rangeTo = now;
                    rangeFrom = now.minusDays(30);
                } else {
                    rangeFrom = from.atStartOfDay();
                    rangeTo = to.atTime(LocalTime.MAX);
                }
            }
            default -> {
                rangeTo = now;
                rangeFrom = now.minusDays(30);
            }
        }

        long periodMillis = ChronoUnit.MILLIS.between(rangeFrom, rangeTo);
        LocalDateTime prevFrom = rangeFrom.minus(periodMillis, ChronoUnit.MILLIS);
        LocalDateTime prevTo = rangeFrom;

        long currentCount = counter.apply(rangeFrom, rangeTo);
        long prevCount = counter.apply(prevFrom, prevTo);

        double delta = prevCount == 0
                ? (currentCount > 0 ? 100 : 0)
                : ((double) (currentCount - prevCount) / prevCount) * 100;
        delta = Math.round(delta * 10.0) / 10.0;

        String trend = delta >= 0 ? "up" : "down";

        long segmentMillis = periodMillis / 12;
        List<Long> sparkline = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDateTime segFrom = rangeFrom.plus(i * segmentMillis, ChronoUnit.MILLIS);
            LocalDateTime segTo = rangeFrom.plus((i + 1) * segmentMillis, ChronoUnit.MILLIS);
            long segCount = counter.apply(segFrom, segTo);
            sparkline.add(segCount);
        }

        DashboardStatResponse result = DashboardStatResponse.builder()
                .total(currentCount)
                .delta(delta)
                .trend(trend)
                .sparkline(sparkline)
                .build();

        return ApiResponse.success("OK", result);
    }
}
