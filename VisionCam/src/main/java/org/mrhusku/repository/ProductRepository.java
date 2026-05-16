package org.mrhusku.repository;

import org.mrhusku.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.category = :category " +
            "AND p.width BETWEEN (:width - 15) AND (:width + 15)")
    List<Product> findSimilarProducts(@Param("category") String category, @Param("width") int width);
    @Query("SELECT p FROM Product p WHERE p.id NOT IN :usedIds ORDER BY RANDOM()")
    List<Product> findRandomExcluding(@Param("usedIds") List<Long> usedIds, Pageable pageable);
    Product findFirstByCategoryOrderByIdAsc(String category);
}