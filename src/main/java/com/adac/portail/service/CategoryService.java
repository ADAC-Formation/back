package com.adac.portail.service;

import com.adac.portail.dto.request.CreateCategoryRequest;
import com.adac.portail.dto.request.UpdateCategoryRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.exception.CategoryAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;

import java.util.List;

/** Formation category management (US-017) — SUPER_ADMIN only for writes, see docs/tech.md § 3. */
public interface CategoryService {

    /** @throws CategoryAlreadyExistsException a category with this name (case-insensitive) already exists */
    CategoryResponse createCategory(CreateCategoryRequest request);

    /** @param active {@code true} restricts to active categories; {@code null}/absent returns all */
    List<CategoryResponse> getCategories(Boolean active);

    /**
     * Updates {@code nom}/{@code couleur} only — {@code isActive} is untouched (use
     * {@link #activateCategory}/{@link #deactivateCategory} for that).
     *
     * @throws ResourceNotFoundException      no category with this id
     * @throws CategoryAlreadyExistsException the new name is already used by a different category
     */
    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    /** Idempotent — activating an already-active category is a no-op, not an error. */
    CategoryResponse activateCategory(Long id);

    /** Idempotent. Formations already referencing this category are left untouched. */
    CategoryResponse deactivateCategory(Long id);
}
