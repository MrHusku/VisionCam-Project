package org.mrhusku.model;

import jakarta.persistence.*;
@Entity
@Table(name = "furniture")

public class Furniture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
    private Integer price;
    private Integer width;
    private Integer height;
    @Column(name="image_url")
    private String imageUrl;

    public Furniture() {}
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public String getName() {return name;}
    public void setName(String name) {this.name = name;}
    public Integer getPrice() {return price;}
    public void setPrice(Integer price) {this.price = price;}
    public Integer getWidth() {return width;}
    public void setWidth(Integer width) {this.width = width;}
    public Integer getHeight() {return height;}
    public void setHeight(Integer height) {this.height = height;}
    public String getImageUrl() {return imageUrl;}
    public void setImageUrl(String imageUrl) {this.imageUrl = imageUrl;}
}
