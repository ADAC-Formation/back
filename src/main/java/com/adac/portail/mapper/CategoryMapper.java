package com.adac.portail.mapper;

import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    // Category.isActive() (property "active") matches CategoryResponse.active — see its Javadoc.
    // No explicit @Mapping needed.
    CategoryResponse toResponse(Category category);
}
