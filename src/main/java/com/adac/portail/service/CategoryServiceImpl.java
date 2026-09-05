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

/** See {@link CategoryService} for the contract; docs/tech.md § 3 for the wire shapes. */
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
                // isActive defaults to true via Category.isActive's @Builder.Default.
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
        // Deliberately doesn't touch Formation/FormationRepository at all — formations already
        // referencing this category keep it as-is (simple FK, no cascade), see docs/tech.md.
        Category category = findOrThrow(id);
        category.setActive(false);
        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /**
     * The {@code existsByNomIgnoreCase*} pre-check in {@link #createCategory} / {@link
     * #updateCategory} is check-then-act, not race-proof — two concurrent calls with the same name
     * can both pass it before either commits. {@code uk_categories_nom_upper}
     * (V2__add_categories.sql) is what actually guarantees uniqueness at the database level; this
     * turns the resulting {@link DataIntegrityViolationException} on the losing request into the
     * same friendly 409 the pre-check produces in the common case, instead of a request that leaks
     * the driver's default 500 (review, TICKET-047).
     *
     * <p>{@code saveAndFlush}, not {@code save}: for {@code updateCategory}, {@code category} is
     * already managed (loaded in this same transaction), so a plain {@code save()} is a no-op
     * {@code merge()} — the actual {@code UPDATE} (and the constraint check with it) would
     * otherwise only run at transaction commit, after this method has already returned normally
     * and this {@code catch} can no longer see it. {@code createCategory}'s {@code IDENTITY}
     * generation strategy already forces an immediate {@code INSERT} either way, so flushing
     * explicitly is a no-op cost there.</p>
     */
    private CategoryResponse saveOrThrowIfNameTaken(Category category) {
        try {
            return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryAlreadyExistsException(NAME_TAKEN_MESSAGE);
        }
    }
}
