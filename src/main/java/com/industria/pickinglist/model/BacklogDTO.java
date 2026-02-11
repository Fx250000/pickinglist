package com.industria.pickinglist.model;

import java.io.Serializable;

public class BacklogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productId;
    private Double quantityRequested; // Changed from Integer
    private Double quantityFound;     // Changed from Integer
    private Double quantityMissing;   // Changed from Integer

    public BacklogDTO() {
    }

    public BacklogDTO(String productId, Double quantityRequested, Double quantityFound, Double quantityMissing) {
        this.productId = productId;
        this.quantityRequested = quantityRequested;
        this.quantityFound = quantityFound;
        this.quantityMissing = quantityMissing;
    }

    // --- Getters and Setters ---
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Double getQuantityRequested() { return quantityRequested; }
    public void setQuantityRequested(Double quantityRequested) { this.quantityRequested = quantityRequested; }

    public Double getQuantityFound() { return quantityFound; }
    public void setQuantityFound(Double quantityFound) { this.quantityFound = quantityFound; }

    public Double getQuantityMissing() { return quantityMissing; }
    public void setQuantityMissing(Double quantityMissing) { this.quantityMissing = quantityMissing; }

    @Override
    public String toString() {
        return "BacklogDTO{" + "productId='" + productId + "', missing=" + quantityMissing + '}';
    }
}