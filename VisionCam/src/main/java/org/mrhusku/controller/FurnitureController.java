package org.mrhusku.controller;

import org.mrhusku.model.Furniture;
import org.mrhusku.service.FurnitureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/furniture")
public class FurnitureController {

    @Autowired
    private FurnitureService furnitureService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFurniture(
            @RequestPart("file") MultipartFile file,
            @RequestParam("knownWidth") int knownWidth
    ) {
        return furnitureService.processMatchAndSave(file, knownWidth);
    }

    @PostMapping(value = "/replace-pro", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> replaceProfessional(
            @RequestParam("roomImage") MultipartFile roomImage,
            @RequestParam("productId") Long productId,
            @RequestParam("maskWidth") int maskWidth,
            @RequestParam("x") int x,
            @RequestParam("y") int y
    ) {
        byte[] imageBytes = furnitureService.processProfessionalInpainting(
                roomImage, productId, maskWidth, x, y);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/generate-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateMultiple(@RequestParam("file") MultipartFile file) {
        byte[] imageBytes = furnitureService.generareMultipla(file);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);

        return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
    }

    @GetMapping
    public List<Furniture> getAllFurniture() {
        return furnitureService.getAllFurniture();
    }
}