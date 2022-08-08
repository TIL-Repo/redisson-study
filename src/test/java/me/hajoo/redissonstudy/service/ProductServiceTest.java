package me.hajoo.redissonstudy.service;

import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;

import me.hajoo.redissonstudy.entity.Product;

class ProductServiceTest {

	private final ProductService productService = new ProductService();

	@Nested
	@DisplayName("수량 100개를 가지는 상품A")
	class CreateProduct {
		Product product = new Product(1L, "상품A", 1000, 100);

		@Nested
		@DisplayName("손님 200명이 상품 하나씩 주문")
		class OrderForEachCustomer {
			int person = 2;
			int orderQuantity = 1;
			CountDownLatch countDownLatch = new CountDownLatch(person);

			@RepeatedTest(20)
			@DisplayName("주문 시작")
			void order() throws Exception {
				IntStream.range(0, person)
						.forEach(x -> {
							Thread thread = new Thread(() -> {
								productService.order(product, orderQuantity);
								countDownLatch.countDown();
							});
							thread.start();
						});
				countDownLatch.await();
				Assertions.assertThat(product.getQuantity()).isZero();
			}
		}
	}
}