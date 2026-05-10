package org.mrhusku.repository;

import org.mrhusku.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.category = :category " +
            "AND p.width BETWEEN (:width - 15) AND (:width + 15)")
    List<Product> findSimilarProducts(@Param("category") String category, @Param("width") int width);

    Product findFirstByCategoryOrderByIdAsc(String category);
}