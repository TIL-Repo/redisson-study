package me.hajoo.redissonstudy.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Product {

	private Long productId;
	private String productName;
	private int price;
	private int quantity;

	public Product(Long productId, String productName, int price, int quantity) {
		this.productId = productId;
		this.productName = productName;
		this.price = price;
		this.quantity = quantity;
	}

	public void decreaseQuantity(int quantity){
		this.quantity -= quantity;
	}
}