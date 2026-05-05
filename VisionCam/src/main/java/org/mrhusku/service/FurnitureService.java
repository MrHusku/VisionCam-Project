package org.mrhusku.service;

import org.mrhusku.model.AiResponse;
import org.mrhusku.model.Furniture;
import org.mrhusku.model.Product;
import org.mrhusku.repository.FurnitureRepository;
import org.mrhusku.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Service
public class FurnitureService {

    @Autowired
    private FurnitureRepository furnitureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RestTemplate restTemplate;

    public Map<String, Object> processMatchAndSave(MultipartFile file, int knownWidth) {
        Furniture measured = processAndSave(file, knownWidth);

        String categoryFromAI = measured.getName();
        String categoryToSearch = categoryFromAI;

        if (categoryFromAI != null) {
            if (categoryFromAI.equalsIgnoreCase("Couch") || categoryFromAI.equalsIgnoreCase("Sofa")) {
                categoryToSearch = "Couch";
            }
        }

        List<Product> recommendations = productRepository.findSimilarProducts(
                categoryToSearch,
                measured.getWidth()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("analysis", measured);
        response.put("recommendations", recommendations);

        return response;
    }

    public Furniture processAndSave(MultipartFile file, int knownWidth) {
        try {
            String urlWithParam = "http://127.0.0.1:8001/analyze?known_width=" + knownWidth;

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AiResponse> response = restTemplate.postForEntity(urlWithParam, requestEntity, AiResponse.class);
            AiResponse result = response.getBody();

            if (result == null || "Unknown".equals(result.getDetectedItem())) {
                throw new RuntimeException("AI-ul nu a detectat niciun obiect valid.");
            }

            Furniture furniture = new Furniture();
            furniture.setName(result.getDetectedItem());

            furniture.setWidth(knownWidth);
            furniture.setHeight(result.getRealHeightCm());

            furniture.setX(result.getX());
            furniture.setY(result.getY());
            furniture.setwPx(result.getwPx());
            furniture.sethPx(result.gethPx());

            return furnitureRepository.save(furniture);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Eroare la procesarea AI: " + e.getMessage());
        }
    }

    public List<Furniture> getAllFurniture() {
        return furnitureRepository.findAll();
    }

    public byte[] processProfessionalInpainting(MultipartFile roomImage, Long productId, int maskWidthPx, int maskX, int maskY) {
        try {
            Product selectedProduct = productRepository.findById(productId)
                    .orElseThrow(() -> new RuntimeException("Produsul nu a fost găsit."));

            // Apelează /analyze automat ca să obții coordonatele reale
            String analyzeUrl = "http://127.0.0.1:8001/analyze?known_width=" + maskWidthPx;
            MultiValueMap<String, Object> analyzeBody = new LinkedMultiValueMap<>();
            analyzeBody.add("file", roomImage.getResource());

            HttpHeaders analyzeHeaders = new HttpHeaders();
            analyzeHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(analyzeBody, analyzeHeaders);
            ResponseEntity<AiResponse> analyzeResponse = restTemplate.postForEntity(analyzeUrl, analyzeRequest, AiResponse.class);
            AiResponse detected = analyzeResponse.getBody();

            // Folosește coordonatele reale detectate de YOLO
            int realX = (detected != null && detected.getX() > 0) ? detected.getX() : maskX;
            int realY = (detected != null && detected.getY() > 0) ? detected.getY() : maskY;
            int realW = (detected != null && detected.getwPx() > 0) ? detected.getwPx() : maskWidthPx;

            // Calculează height-ul pe baza proporțiilor produsului
            int realHeightCm = selectedProduct.getHeight();
            int realWidthCm = selectedProduct.getWidth();
            int realH = (realHeightCm * realW) / realWidthCm;

            //  Trimite la Python cu coordonate corecte
            String forgeProxyUrl = "http://127.0.0.1:8001/generate_pro";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("room_file", roomImage.getResource());
            body.add("product_image_url", selectedProduct.getImageUrl());
            body.add("x", realX);
            body.add("y", realY);
            body.add("w_px", realW);
            body.add("h_px", realH);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.postForEntity(forgeProxyUrl, requestEntity, byte[].class);

            return response.getBody();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Eroare la generarea profesională: " + e.getMessage());
        }
    }
}