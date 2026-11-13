package com.chtrembl.petstore.pet.service;

import com.chtrembl.petstore.pet.model.Category;
import com.chtrembl.petstore.pet.model.Pet;
import com.chtrembl.petstore.pet.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PetService {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_PETS = """
            SELECT
                p.id AS pet_id,
                p.name AS pet_name,
                p.photourl AS photo_url,
                p.status AS pet_status,
                c.id AS category_id,
                c.name AS category_name,
                t.id AS tag_id,
                t.name AS tag_name
            FROM public.pet p
            JOIN public.category c ON c.id = p.category_id
            LEFT JOIN public.pet_tag pt ON pt.pet_id = p.id
            LEFT JOIN public.tag t ON t.id = pt.tag_id
            ORDER BY p.id, t.id
            """;

    public List<Pet> getAllPets() {
        log.info("Getting pets from PostgreSQL");

        return jdbcTemplate.query(
                SELECT_PETS,
                (ResultSetExtractor<List<Pet>>) rs -> {
                    Map<Long, Pet> pets = new LinkedHashMap<>();

                    while (rs.next()) {
                        long petId = rs.getLong("pet_id");

                        Pet pet = pets.get(petId);
                        if (pet == null) {
                            Category category = new Category();
                            category.setId(rs.getLong("category_id"));
                            category.setName(rs.getString("category_name"));

                            pet = new Pet();
                            pet.setId(petId);
                            pet.setName(rs.getString("pet_name"));
                            pet.setPhotoURL(rs.getString("photo_url"));
                            pet.setStatus(
                                    Pet.Status.fromValue(
                                            rs.getString("pet_status")));
                            pet.setCategory(category);
                            pet.setTags(new ArrayList<>());

                            pets.put(petId, pet);
                        }

                        Object tagId = rs.getObject("tag_id");
                        if (tagId != null) {
                            Tag tag = new Tag();
                            tag.setId(rs.getLong("tag_id"));
                            tag.setName(rs.getString("tag_name"));
                            pet.getTags().add(tag);
                        }
                    }

                    return new ArrayList<>(pets.values());
                });
    }

    public List<Pet> findPetsByStatus(List<String> status) {
        log.info("Finding PostgreSQL pets with status: {}", status);

        return getAllPets().stream()
                .filter(pet -> status.contains(pet.getStatus().getValue()))
                .toList();
    }

    public Optional<Pet> findPetById(Long petId) {
        log.info("Finding PostgreSQL pet with id: {}", petId);

        return getAllPets().stream()
                .filter(pet -> pet.getId().equals(petId))
                .findFirst();
    }

    public int getPetCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.pet",
                Integer.class);

        return count != null ? count : 0;
    }
}
