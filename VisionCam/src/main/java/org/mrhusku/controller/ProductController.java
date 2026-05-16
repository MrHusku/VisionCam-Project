package org.mrhusku.controller;

import org.mrhusku.model.Product;
import org.mrhusku.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    // GET /api/products?ids=1,2,3
    @GetMapping
    public List<Product> getProductsByIds(@RequestParam List<Long> ids) {
        return productRepository.findAllById(ids);
    }

    // GET /api/products/{id}
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }
}