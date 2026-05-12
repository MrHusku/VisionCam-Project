package org.mrhusku.repository;

import org.mrhusku.model.Furniture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.mrhusku.model.User;
import java.util.List;

@Repository
public interface FurnitureRepository extends JpaRepository<Furniture, Long> {


    List<Furniture> findByUser(User user);
}