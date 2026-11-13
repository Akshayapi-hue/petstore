package com.chtrembl.petstore.product.service;

import com.chtrembl.petstore.product.model.Category;
import com.chtrembl.petstore.product.model.Product;
import com.chtrembl.petstore.product.model.Tag;
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
public class ProductService {

    private final JdbcTemplate jdbcTemplate;

    private static final String SELECT_PRODUCTS = """
            SELECT
                p.id AS product_id,
                p.name AS product_name,
                p.photourl AS photo_url,
                p.status AS product_status,
                c.id AS category_id,
                c.name AS category_name,
                t.id AS tag_id,
                t.name AS tag_name
            FROM public.product p
            JOIN public.category c ON c.id = p.category_id
            LEFT JOIN public.product_tag pt ON pt.product_id = p.id
            LEFT JOIN public.tag t ON t.id = pt.tag_id
            ORDER BY p.id, t.id
            """;

    public List<Product> getAllProducts() {
        log.info("Getting products from PostgreSQL");

        return jdbcTemplate.query(
                SELECT_PRODUCTS,
                (ResultSetExtractor<List<Product>>) rs -> {
                    Map<Long, Product> products = new LinkedHashMap<>();

                    while (rs.next()) {
                        long productId = rs.getLong("product_id");

                        Product product = products.get(productId);
                        if (product == null) {
                            Category category = new Category();
                            category.setId(rs.getLong("category_id"));
                            category.setName(rs.getString("category_name"));

                            product = new Product();
                            product.setId(productId);
                            product.setName(rs.getString("product_name"));
                            product.setPhotoURL(rs.getString("photo_url"));
                            product.setStatus(
                                    Product.Status.fromValue(
                                            rs.getString("product_status")));
                            product.setCategory(category);
                            product.setTags(new ArrayList<>());

                            products.put(productId, product);
                        }

                        Object tagId = rs.getObject("tag_id");
                        if (tagId != null) {
                            Tag tag = new Tag();
                            tag.setId(rs.getLong("tag_id"));
                            tag.setName(rs.getString("tag_name"));
                            product.getTags().add(tag);
                        }
                    }

                    return new ArrayList<>(products.values());
                });
    }

    public List<Product> findProductsByStatus(List<String> status) {
        log.info("Finding PostgreSQL products with status: {}", status);

        return getAllProducts().stream()
                .filter(product ->
                        status.contains(product.getStatus().getValue()))
                .toList();
    }

    public Optional<Product> findProductById(Long productId) {
        log.info("Finding PostgreSQL product with id: {}", productId);

        return getAllProducts().stream()
                .filter(product -> product.getId().equals(productId))
                .findFirst();
    }

    public int getProductCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.product",
                Integer.class);

        return count != null ? count : 0;
    }
}
