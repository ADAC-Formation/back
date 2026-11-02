package com.adac.portail.mapper;

import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
