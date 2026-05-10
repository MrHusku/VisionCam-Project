package org.mrhusku.service;

import org.mrhusku.model.*;
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
                    .orElseThrow(() -> new RuntimeException("Produsul nu a fost gasit."));

            String analyzeUrl = "http://127.0.0.1:8001/analyze?known_width=" + maskWidthPx;
            MultiValueMap<String, Object> analyzeBody = new LinkedMultiValueMap<>();
            analyzeBody.add("file", roomImage.getResource());

            HttpHeaders analyzeHeaders = new HttpHeaders();
            analyzeHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(analyzeBody, analyzeHeaders);
            ResponseEntity<AiResponse> analyzeResponse = restTemplate.postForEntity(analyzeUrl, analyzeRequest, AiResponse.class);
            AiResponse detected = analyzeResponse.getBody();


            int realX = (detected != null && detected.getX() > 0) ? detected.getX() : maskX;
            int realY = (detected != null && detected.getY() > 0) ? detected.getY() : maskY;
            int realW = (detected != null && detected.getwPx() > 0) ? detected.getwPx() : maskWidthPx;
            int realH = (detected != null && detected.gethPx() > 0) ? detected.gethPx() : (maskWidthPx * 60);

            String forgeProxyUrl = "http://127.0.0.1:8001/generate_pro";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("room_file", roomImage.getResource());
            body.add("product_image_url", selectedProduct.getImageUrl());

            body.add("product_category", selectedProduct.getCategory());

            body.add("x", realX);
            body.add("y", realY);
            body.add("w_px", realW);
            body.add("h_px", realH);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<byte[]> response = restTemplate.postForEntity(forgeProxyUrl, requestEntity, byte[].class);
                return response.getBody();
            } catch (org.springframework.web.client.HttpClientErrorException.BadRequest ex) {
                String errorDetails = ex.getResponseBodyAsString();
                System.err.println("Eroare Validare Categorii AI: " + errorDetails);
                throw new RuntimeException("Incompatibilitate detectata: " + errorDetails);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Eroare la generarea : " + e.getMessage());
        }
    }
    public byte[] generareMultipla(MultipartFile originalRoomImage) {
        try {
            byte[] currentImageBytes = originalRoomImage.getBytes();

            MultipartFile initialFile = new CustomMultipartFile(currentImageBytes, "file", "image.png");
            AiAnalysisResponse analysis = restTemplate.postForObject(
                    "http://127.0.0.1:8001/analyze?known_width=1000",
                    new HttpEntity<>(createBody(initialFile), createHeaders()),
                    AiAnalysisResponse.class
            );

            if (analysis == null || analysis.getItems() == null) return currentImageBytes;
            for (AiResponse detected : analysis.getItems()) {

                String categorieDetectata = detected.getDetectedItem();
                Product p = productRepository.findFirstByCategoryOrderByIdAsc(categorieDetectata);

                if (p == null) {
                    System.out.println("Nu am gasit niciun produs in DB pentru categoria : " + categorieDetectata);
                    continue;
                }

                MultipartFile currentStepFile = new CustomMultipartFile(currentImageBytes, "room_file", "image.png");
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("room_file", currentStepFile.getResource());
                body.add("product_image_url", p.getImageUrl());
                body.add("product_category", p.getCategory());
                body.add("x", detected.getX());
                body.add("y", detected.getY());
                body.add("w_px", detected.getwPx());

                body.add("h_px", detected.gethPx());

                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, createHeaders());
                ResponseEntity<byte[]> response = restTemplate.postForEntity("http://127.0.0.1:8001/generate_pro", request, byte[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    currentImageBytes = response.getBody();
                }
            }
            return currentImageBytes;

        } catch (Exception e) {
            throw new RuntimeException("Eroare la procesarea automata: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private MultiValueMap<String, Object> createBody(MultipartFile file) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        return body;
    }
}