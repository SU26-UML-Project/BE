package su26.uml.be.mapper;

import org.mapstruct.*;
import su26.uml.be.dto.request.SheetRequest;
import su26.uml.be.dto.response.SheetResponse;
import su26.uml.be.entity.Sheet;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SheetMapper {
    Sheet toSheet(SheetRequest request);

    @Mapping(target = "projectId", source = "project.id")
    SheetResponse toSheetResponse(Sheet sheet);

    List<SheetResponse> toSheetResponseList(List<Sheet> sheets);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateSheet(SheetRequest request, @MappingTarget Sheet sheet);
}
