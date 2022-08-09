package me.hajoo.redissonstudy.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import me.hajoo.redissonstudy.entity.Product;

class ProductServiceTest {

	private final ProductService productService = new ProductService();
	private final RedissonClient redissonClient = Redisson.create();

	@Nested
	@DisplayName("수량 50개를 가지는 상품A")
	class CreateProduct {
		Product product = new Product(1L, "상품A", 1000, 50);

		@Nested
		@DisplayName("손님 50이 상품 하나씩 주문")
		class OrderForEachCustomer {
			int person = 50;
			int orderQuantity = 1;
			CountDownLatch countDownLatch;

			@RepeatedTest(10)
			@DisplayName("주문 시작 (LOCK OFF)")
			void order() throws Exception {
				countDownLatch = new CountDownLatch(person);
				IntStream.range(0, person + 1)
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

			@RepeatedTest(10)
			@DisplayName("주문 시작 (LOCK ON)")
			void orderForLock() throws Exception {
				countDownLatch = new CountDownLatch(person);
				IntStream.range(0, person + 1)
					.forEach(x -> {
						Thread thread = new Thread(() -> {
							productService.orderForLock(product, orderQuantity);
							countDownLatch.countDown();
						});
						thread.start();
					});
				countDownLatch.await();
				Assertions.assertThat(product.getQuantity()).isZero();
			}
		}
	}

	@Test
	void lock() throws Exception {
		// given
		Thread thread = new Thread(new Worker());
		Thread thread2 = new Thread(new Worker());
		thread.setName("일꾼1");
		thread2.setName("일꾼2");

		// when
		thread.start();
		thread2.start();
		thread.join();
		thread2.join();
	}

	class Worker implements Runnable {
		@Override
		public void run() {
			RLock lock = redissonClient.getLock("lock");
			lock.lock(10, TimeUnit.SECONDS);
			String workerName = Thread.currentThread().getName();
			System.out.println(workerName + "가 LOCK 획득");
			for (int i = 0; i < 5; i++) {
				System.out.println(workerName + "는 working...");
				rest();
			}
			System.out.println(workerName + "가 LOCK 해제");
			lock.unlock();
		}

		private void rest(){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Test
	void tryLock() throws Exception {
		// given
		Thread thread = new Thread(new TryWorker());
		Thread thread2 = new Thread(new TryWorker());
		thread.setName("일꾼1");
		thread2.setName("일꾼2");

		// when
		thread.start();
		thread2.start();
		thread.join();
		thread2.join();
	}

	class TryWorker implements Runnable {
		@Override
		public void run() {
			String workerName = Thread.currentThread().getName();
			RLock lock = redissonClient.getLock("lock");
			try {
				System.out.println(workerName + "이 LOCK을 기다립니다.");
				tryLock(lock);
				System.out.println(workerName + "가 LOCK 획득");
				for (int i = 0; i < 5; i++) {
					System.out.println(workerName + "는 working...");
					rest();
				}
				System.out.println(workerName + "가 LOCK 해제");
				lock.unlock();
			} catch (Exception e) {}
		}

		private void tryLock(RLock lock) throws Exception {
			try {
				if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
					System.out.println(Thread.currentThread().getName() + "는 기다리다가 지쳐서 돌아갑니다.");
					throw new Exception();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				throw e;
			}
		}

		private void rest(){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}