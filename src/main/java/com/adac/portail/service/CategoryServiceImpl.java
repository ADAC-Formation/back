package com.adac.portail.service;

import com.adac.portail.dto.request.CreateCategoryRequest;
import com.adac.portail.dto.request.UpdateCategoryRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.exception.CategoryAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.CategoryMapper;
import com.adac.portail.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String NAME_TAKEN_MESSAGE = "Cette catégorie existe déjà";
    private static final String NOT_FOUND_MESSAGE = "Catégorie introuvable";

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByNomIgnoreCase(request.getNom())) {
            throw new CategoryAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
        Category category = Category.builder()
                .nom(request.getNom())
                .couleur(request.getCouleur())

                .build();
        return saveOrThrowIfNameTaken(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(Boolean active) {
        List<Category> categories = Boolean.TRUE.equals(active)
                ? categoryRepository.findAllByIsActiveTrue()
                : categoryRepository.findAll();
        return categories.stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = findOrThrow(id);
        if (categoryRepository.existsByNomIgnoreCaseAndIdNot(request.getNom(), id)) {
            throw new CategoryAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
        category.setNom(request.getNom());
        category.setCouleur(request.getCouleur());
        return saveOrThrowIfNameTaken(category);
    }

    @Override
    @Transactional
    public CategoryResponse activateCategory(Long id) {
        Category category = findOrThrow(id);
        category.setActive(true);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse deactivateCategory(Long id) {

        Category category = findOrThrow(id);
        category.setActive(false);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private CategoryResponse saveOrThrowIfNameTaken(Category category) {
        try {
            return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
    }
}
