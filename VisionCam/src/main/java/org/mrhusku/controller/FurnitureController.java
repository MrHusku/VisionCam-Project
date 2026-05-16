package org.mrhusku.controller;

import org.mrhusku.model.Furniture;
import org.mrhusku.model.User;
import org.mrhusku.repository.UserRepository;
import org.mrhusku.service.FurnitureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import org.mrhusku.model.MultipleGenerationResult;
import java.util.Base64;
import java.util.HashMap;

@RestController
@RequestMapping("/api/furniture")
public class FurnitureController {

    @Autowired
    private FurnitureService furnitureService;
    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFurniture(
            @RequestPart("file") MultipartFile file,
            @RequestParam("knownWidth") int knownWidth
    ) { User currentUser = getCurrentUser();
        return furnitureService.processMatchAndSave(file, knownWidth,currentUser);
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
    public ResponseEntity<Map<String, Object>> generateMultiple(@RequestParam("file") MultipartFile file) {
        MultipleGenerationResult result = furnitureService.generareMultipla(file);

        String base64Image = Base64.getEncoder().encodeToString(result.getImage());

        Map<String, Object> response = new HashMap<>();
        response.put("image", base64Image);
        response.put("productIds", result.getProductIds());

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<Furniture> getAllFurniture() {
        User currentUser = getCurrentUser();
        return furnitureService.getFurnitureByUser(currentUser);
    }
}