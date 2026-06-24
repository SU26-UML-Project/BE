package su26.uml.be.service;

import su26.uml.be.dto.request.SheetRequest;
import su26.uml.be.dto.response.ApiResponse;
import su26.uml.be.dto.response.SheetResponse;

import java.util.List;
import java.util.UUID;

public interface SheetService {
    ApiResponse<SheetResponse> createSheet(SheetRequest request);
    ApiResponse<SheetResponse> updateSheet(UUID sheetId, SheetRequest request);
    ApiResponse<Void> deleteSheet(UUID sheetId);
    ApiResponse<SheetResponse> getSheetById(UUID sheetId);
    ApiResponse<List<SheetResponse>> getSheetsByProject(UUID projectId);
}
