package me.hajoo.redissonstudy.service;

import me.hajoo.redissonstudy.entity.Product;

public class ProductService {

	public void order(Product product, int quantity) {
		product.decreaseQuantity(quantity);
	}
}
