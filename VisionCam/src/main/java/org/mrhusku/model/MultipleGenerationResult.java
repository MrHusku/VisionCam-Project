package org.mrhusku.model;

import java.util.List;

public class MultipleGenerationResult {
    private byte[] image;
    private List<Long> productIds;

    public MultipleGenerationResult(byte[] image, List<Long> productIds) {
        this.image = image;
        this.productIds = productIds;
    }

    public byte[] getImage() { return image; }
    public List<Long> getProductIds() { return productIds; }
}