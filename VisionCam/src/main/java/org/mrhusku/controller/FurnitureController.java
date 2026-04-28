package org.mrhusku.controller;
import org.mrhusku.model.Furniture;
import org.mrhusku.service.FurnitureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/furniture")

public class FurnitureController {
    @Autowired
    private FurnitureService furnitureService;
    @GetMapping
    public List<Furniture> getAllFurniture(){
        return furnitureService.getAllFurniture();
    }
    @PostMapping("/upload")
    public Furniture uploadFurniture(@RequestParam("file") MultipartFile file){
        return furnitureService.processAndSave(file);
    }
}
