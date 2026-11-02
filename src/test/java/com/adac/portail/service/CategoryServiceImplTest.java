package com.adac.portail.service;

import com.adac.portail.dto.request.CreateCategoryRequest;
import com.adac.portail.dto.request.UpdateCategoryRequest;
import com.adac.portail.dto.response.CategoryResponse;
import com.adac.portail.entity.Category;
import com.adac.portail.exception.CategoryAlreadyExistsException;
import com.adac.portail.exception.ResourceNotFoundException;
import com.adac.portail.mapper.CategoryMapper;
import com.adac.portail.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createCategorySavesActiveCategoryAndReturnsMappedResponse() {
        CreateCategoryRequest request = new CreateCategoryRequest("Formation SST", "#FF5733");
        when(categoryRepository.existsByNomIgnoreCase("Formation SST")).thenReturn(false);
        Category saved = Category.builder().id(1L).nom("Formation SST").couleur("#FF5733").build();
        when(categoryRepository.saveAndFlush(any())).thenReturn(saved);
        CategoryResponse expected = CategoryResponse.builder().id(1L).nom("Formation SST").build();
        when(categoryMapper.toResponse(saved)).thenReturn(expected);

        CategoryResponse result = categoryService.createCategory(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNom()).isEqualTo("Formation SST");
        assertThat(captor.getValue().getCouleur()).isEqualTo("#FF5733");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void createCategoryWithDuplicateNameThrowsAndNeverSaves() {
        CreateCategoryRequest request = new CreateCategoryRequest("estime de soi en travail social", "#FF5733");
        when(categoryRepository.existsByNomIgnoreCase("estime de soi en travail social")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCategoryLosingConcurrentRaceThrowsCategoryAlreadyExists() {
        CreateCategoryRequest request = new CreateCategoryRequest("Formation SST", "#FF5733");
        when(categoryRepository.existsByNomIgnoreCase("Formation SST")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_categories_nom_upper"));

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(CategoryAlreadyExistsException.class);
    }

    @Test
    void getCategoriesWithNoFilterReturnsAllCategories() {
        Category active = Category.builder().id(1L).isActive(true).build();
        Category inactive = Category.builder().id(2L).isActive(false).build();
        when(categoryRepository.findAll()).thenReturn(List.of(active, inactive));
        when(categoryMapper.toResponse(any())).thenReturn(CategoryResponse.builder().build());

        List<CategoryResponse> result = categoryService.getCategories(null);

        assertThat(result).hasSize(2);
        verify(categoryRepository, never()).findAllByIsActiveTrue();
    }

    @Test
    void getCategoriesWithActiveTrueReturnsOnlyActiveOnes() {
        Category active = Category.builder().id(1L).isActive(true).build();
        when(categoryRepository.findAllByIsActiveTrue()).thenReturn(List.of(active));
        when(categoryMapper.toResponse(active)).thenReturn(CategoryResponse.builder().id(1L).build());

        List<CategoryResponse> result = categoryService.getCategories(true);

        assertThat(result).hasSize(1);
        verify(categoryRepository, never()).findAll();
    }

    @Test
    void updateCategoryChangesNomAndCouleurWithoutTouchingIsActive() {
        Category existing = Category.builder().id(1L).nom("Old name").couleur("#000000").isActive(false).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNomIgnoreCaseAndIdNot("New name", 1L)).thenReturn(false);
        when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);
        when(categoryMapper.toResponse(existing)).thenReturn(CategoryResponse.builder().id(1L).build());

        categoryService.updateCategory(1L, new UpdateCategoryRequest("New name", "#111111"));

        assertThat(existing.getNom()).isEqualTo("New name");
        assertThat(existing.getCouleur()).isEqualTo("#111111");
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void updateCategoryWithUnchangedNomOnlyChangesCouleur() {
        Category existing = Category.builder().id(1L).nom("SST").couleur("#000000").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNomIgnoreCaseAndIdNot("SST", 1L)).thenReturn(false);
        when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);
        when(categoryMapper.toResponse(existing)).thenReturn(CategoryResponse.builder().id(1L).build());

        categoryService.updateCategory(1L, new UpdateCategoryRequest("SST", "#111111"));

        assertThat(existing.getNom()).isEqualTo("SST");
        assertThat(existing.getCouleur()).isEqualTo("#111111");
    }

    @Test
    void updateCategoryWithUnknownIdThrowsResourceNotFound() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(404L, new UpdateCategoryRequest("x", "#000000")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCategoryWithNameTakenByAnotherCategoryThrows() {
        Category existing = Category.builder().id(1L).nom("Old name").couleur("#000000").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNomIgnoreCaseAndIdNot("Taken", 1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1L, new UpdateCategoryRequest("Taken", "#000000")))
                .isInstanceOf(CategoryAlreadyExistsException.class);

        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateCategoryLosingConcurrentRaceThrowsCategoryAlreadyExists() {
        Category existing = Category.builder().id(1L).nom("Old name").couleur("#000000").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNomIgnoreCaseAndIdNot("Taken", 1L)).thenReturn(false);
        when(categoryRepository.saveAndFlush(existing)).thenThrow(new DataIntegrityViolationException("uk_categories_nom_upper"));

        assertThatThrownBy(() -> categoryService.updateCategory(1L, new UpdateCategoryRequest("Taken", "#000000")))
                .isInstanceOf(CategoryAlreadyExistsException.class);
    }

    @Test
    void deactivateThenActivateRestoresIsActiveWithoutTouchingFormations() {
        Category category = Category.builder().id(1L).isActive(true).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(CategoryResponse.builder().id(1L).build());

        categoryService.deactivateCategory(1L);
        assertThat(category.isActive()).isFalse();

        categoryService.activateCategory(1L);
        assertThat(category.isActive()).isTrue();
    }

    @Test
    void activateAlreadyActiveCategoryIsIdempotent() {
        Category category = Category.builder().id(1L).isActive(true).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);
        CategoryResponse expected = CategoryResponse.builder().id(1L).build();
        when(categoryMapper.toResponse(category)).thenReturn(expected);

        CategoryResponse result = categoryService.activateCategory(1L);

        assertThat(result).isSameAs(expected);
        assertThat(category.isActive()).isTrue();
        verify(categoryRepository).save(category);
    }

    @Test
    void activateCategoryWithUnknownIdThrowsResourceNotFound() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.activateCategory(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateCategoryWithUnknownIdThrowsResourceNotFound() {
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deactivateCategory(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
