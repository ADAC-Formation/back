package com.adac.portail.service;

import com.adac.portail.dto.request.CreateCategoryRequest;
import com.adac.portail.dto.request.UpdateCategoryRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.exception.CategoryAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;

import java.util.List;

public interface CategoryService {

    CategoryResponse createCategory(CreateCategoryRequest request);

    List<CategoryResponse> getCategories(Boolean active);

    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    CategoryResponse activateCategory(Long id);

    CategoryResponse deactivateCategory(Long id);
}
