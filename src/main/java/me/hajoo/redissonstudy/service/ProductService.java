package me.hajoo.redissonstudy.service;

import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import me.hajoo.redissonstudy.entity.Product;

public class ProductService {

	private final RedissonClient redissonClient = Redisson.create();

	public void order(Product product, int quantity) {
		product.decreaseQuantity(quantity);
	}

	public void orderForLock(Product product, int quantity) {
		RLock lock = redissonClient.getLock("lock");
		if (tryLock(lock)) {
			product.decreaseQuantity(quantity);
			lock.unlock();
		}
	}

	private boolean tryLock(RLock lock) {
		try {
			return lock.tryLock(3, 3, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
}
