package su26.uml.be.service;

import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.DashboardStatResponse;

import java.time.LocalDate;

public interface DashboardService {
    ApiResponse<DashboardStatResponse> getUserStats(String range, LocalDate from, LocalDate to);
    ApiResponse<DashboardStatResponse> getProjectStats(String range, LocalDate from, LocalDate to);
    ApiResponse<DashboardStatResponse> getDiagramStats(String range, LocalDate from, LocalDate to);
}
