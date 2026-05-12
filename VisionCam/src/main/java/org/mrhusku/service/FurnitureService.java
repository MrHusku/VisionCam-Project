package org.mrhusku.service;

import org.mrhusku.exceptions.AiServiceException;
import org.mrhusku.exceptions.ResourceNotFoundException;
import org.mrhusku.model.*;
import org.mrhusku.repository.FurnitureRepository;
import org.mrhusku.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FurnitureService {

    @Autowired
    private FurnitureRepository furnitureRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Processes an image, saves the analysis linked to a user, and returns recommendations.
     */
    public Map<String, Object> processMatchAndSave(MultipartFile file, int knownWidth, User user) {
        Furniture measured = processAndSave(file, knownWidth, user);

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

    /**
     * Communicates with AI Python Server to analyze the image.
     */
    public Furniture processAndSave(MultipartFile file, int knownWidth, User user) {
        try {
            String urlWithParam = "http://127.0.0.1:8001/analyze?known_width=" + knownWidth;

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(createBody(file), createHeaders());

            ResponseEntity<AiAnalysisResponse> response = restTemplate.postForEntity(
                    urlWithParam, requestEntity, AiAnalysisResponse.class
            );

            AiAnalysisResponse result = response.getBody();

            if (result == null || result.getItems() == null || result.getItems().isEmpty()) {
                throw new AiServiceException("AI could not detect any valid furniture in the image.");
            }

            AiResponse firstItem = result.getItems().get(0);

            if ("Unknown".equals(firstItem.getDetectedItem())) {
                throw new AiServiceException("AI could not detect any valid furniture in the image.");
            }

            Furniture furniture = new Furniture();
            furniture.setName(firstItem.getDetectedItem());
            furniture.setWidth(knownWidth);
            furniture.setHeight(firstItem.getRealHeightCm());
            furniture.setX(firstItem.getX());
            furniture.setY(firstItem.getY());
            furniture.setwPx(firstItem.getwPx());
            furniture.sethPx(firstItem.gethPx());
            furniture.setUser(user);

            return furnitureRepository.save(furniture);

        } catch (HttpClientErrorException e) {
            throw new AiServiceException("External AI server returned an error: " + e.getResponseBodyAsString());
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Failed to process image: " + e.getMessage());
        }
    }

    /**
     * Returns only the furniture belonging to the specific user.
     */
    public List<Furniture> getFurnitureByUser(User user) {
        return furnitureRepository.findByUser(user);
    }

    /**
     * Handles professional inpainting using a selected product.
     */
    public byte[] processProfessionalInpainting(MultipartFile roomImage, Long productId, int maskWidthPx, int maskX, int maskY) {
        Product selectedProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        try {
            // First: Analyze the spot
            String analyzeUrl = "http://127.0.0.1:8001/analyze?known_width=" + maskWidthPx;
            HttpEntity<MultiValueMap<String, Object>> analyzeRequest = new HttpEntity<>(createBody(roomImage), createHeaders());

            ResponseEntity<AiResponse> analyzeResponse = restTemplate.postForEntity(analyzeUrl, analyzeRequest, AiResponse.class);
            AiResponse detected = analyzeResponse.getBody();

            int realX = (detected != null && detected.getX() > 0) ? detected.getX() : maskX;
            int realY = (detected != null && detected.getY() > 0) ? detected.getY() : maskY;
            int realW = (detected != null && detected.getwPx() > 0) ? detected.getwPx() : maskWidthPx;
            int realH = (detected != null && detected.gethPx() > 0) ? detected.gethPx() : (maskWidthPx * 60);

            // Second: Generate the inpainting
            String forgeProxyUrl = "http://127.0.0.1:8001/generate_pro";
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("room_file", roomImage.getResource());
            body.add("product_image_url", selectedProduct.getImageUrl());
            body.add("product_category", selectedProduct.getCategory());
            body.add("x", realX);
            body.add("y", realY);
            body.add("w_px", realW);
            body.add("h_px", realH);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, createHeaders());

            ResponseEntity<byte[]> response = restTemplate.postForEntity(forgeProxyUrl, requestEntity, byte[].class);
            return response.getBody();

        } catch (HttpClientErrorException.BadRequest ex) {
            throw new AiServiceException("AI Incompatibility: " + ex.getResponseBodyAsString());
        } catch (Exception e) {
            throw new AiServiceException("Professional generation failed: " + e.getMessage());
        }
    }

    public byte[] generareMultipla(MultipartFile originalRoomImage) {
        try {
            BufferedImage bufferedImage = ImageIO.read(originalRoomImage.getInputStream());
            int imageWidth = bufferedImage.getWidth();

            byte[] currentImageBytes = originalRoomImage.getBytes();

            AiAnalysisResponse analysis = restTemplate.postForObject(
                    "http://127.0.0.1:8001/analyze?known_width=" + imageWidth,
                    new HttpEntity<>(createBody(originalRoomImage), createHeaders()),
                    AiAnalysisResponse.class
            );

            if (analysis == null || analysis.getItems() == null) return currentImageBytes;

            for (AiResponse detected : analysis.getItems()) {
                String category = detected.getDetectedItem();
                Product p = productRepository.findFirstByCategoryOrderByIdAsc(category);

                if (p == null) continue;

                // Creating a custom multipart from bytes for the next step
                MultipartFile currentStepFile = new CustomMultipartFile(currentImageBytes, "room_file", "image.png");

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("room_file", currentStepFile.getResource());
                body.add("product_image_url", p.getImageUrl());
                body.add("product_category", p.getCategory());
                body.add("x", detected.getX());
                body.add("y", detected.getY());
                body.add("w_px", detected.getwPx());
                body.add("h_px", detected.gethPx());

                ResponseEntity<byte[]> response = restTemplate.postForEntity("http://127.0.0.1:8001/generate_pro",
                        new HttpEntity<>(body, createHeaders()), byte[].class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    currentImageBytes = response.getBody();
                }
            }
            return currentImageBytes;

        } catch (Exception e) {
            throw new AiServiceException("Multiple generation process failed: " + e.getMessage());
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

