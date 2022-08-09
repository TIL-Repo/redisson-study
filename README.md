# Redisson으로 분산 락 구현

- Redis Java Client이다.
- 여러 개의 독립된 프로세스가 임계 영역에 접근할 때 제대로 된 데이터 처리를 위해서 하나씩 요청을 수행해야 한다.
- 이때 Lock을 이용하여 동시성을 보장할 수 있고 이것을 구현하는 데 Redisson 프레임워크를 이용하여 손쉽게 구현할 수 있다.

## 특징

### Lock에 Timeout 구현

- Timeout을 구현해놨기 때문에 데드락 상태를 피할 수 있다. 

```java
boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
```

### 스핀 락 발생 x

- 스핀 락이 발생하면 lock을 얻으려고 하기 때문에 트래픽이 증가하고 요청 응답시간이 점점 늘어나게 된다.
- Redisson은 pubsub 기능을 이용해서 계속해서 값을 확인하는 것이 아니라 구독하고 있는 클라이언트에게 lock이 해제되면 알림을 주는 방식으로 개선했다.

### Lua 스크립트 사용

- lock이 사용하는 기능들은 atomic 해야 한다.
  - lock의 획득 가능 여부 알림 (publish-subscribe)
  - lock 획득, 해제
- Redis는 싱글 스레드 기반으로 동작하고 Lua 스크립트를 이용하여 쉽게 atomic한 연산을 구현할 수 있고 그로 인해 요청이 줄고 성능을 높일 수 있다.

## 실습

- lock, tryLock 동작과 동시 여러 주문이 발생한 상황에 했을 때 동시성을 보장하는 코드를 작성해보자.

### Lock

- leaseTime 동안 lock을 획득한다.

```java
void lock(long leaseTime, TimeUnit unit);
```

- 일꾼1, 일꾼2 서로 다른 두 일꾼(스레드)가 lock을 획득하여 5초동안 일을 한다.

```java
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
```

- 일꾼1이 먼저 lock을 획득했고 일을 끝낸 후 lock을 반환하고 일꾼2가 lock을 얻어 일을 진행하는 것을 알 수 있다.

```bash
일꾼1가 LOCK 획득
일꾼1는 working...
일꾼1는 working...
일꾼1는 working...
일꾼1는 working...
일꾼1는 working...
일꾼1가 LOCK 해제
일꾼2가 LOCK 획득
일꾼2는 working...
일꾼2는 working...
일꾼2는 working...
일꾼2는 working...
일꾼2는 working...
일꾼2가 LOCK 해제
```

### TryLock

- leaseTime 동안 lock을 획득하며 `lock()`과 차이점은 waitTime이 있어 lock 획득에 있어 그 이상의 시간이 걸리게 되면 획득을 포기한다.

```java
boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
```

- 일꾼1, 일꾼2 서로 다른 두 일꾼(스레드)가 lock을 획득하여 5초동안 일한다.
- 차이점은 waitTime을 3초로 주어 다른 일꾼 일하는 동안 기다리다가 일꾼은 도망가게 되는 시나리오이다.

```java
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
```

- 일꾼2가 lock을 획득하여 일을 시작하고 일하는 동안 일꾼1는 lock을 기다리다가 지쳐 돌아가는 것을 알 수 있다.

```bash
일꾼1이 LOCK을 기다립니다.
일꾼2이 LOCK을 기다립니다.
일꾼2가 LOCK 획득
일꾼2는 working...
일꾼2는 working...
일꾼2는 working...
일꾼1는 기다리다가 지쳐서 돌아갑니다.
일꾼2는 working...
일꾼2는 working...
일꾼2가 LOCK 해제
```

### 동시 여러 주문 상황

- 상품 클래스 생성

```java
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
```

- 주문했을 때 재고 감소하는 로직 구현

```java
public class ProductService {

    private final RedissonClient redissonClient = Redisson.create();

    // 동시성 보장 X
    public void order(Product product, int quantity) {
        product.decreaseQuantity(quantity);
    }
    
    // 동시성 보장 O
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
```

- 수량 50개를 가지는 상품이 있고 손님 50명이 하나씩 주문한다.
- lock이 없으면 특정 상품에 요청이 동시에 들어올 경우 같은 값을 바라보고 재고 감소 처리하기 때문에 제대로 된 처리가 안 된다.
- lock이 있는 경우에는 하나씩 처리하기 때문에 동시성이 보장되기 때문에 값 처리가 제대로 되는 것을 알 수 있다.

```java
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
```

## References

- [https://github.com/redisson/redisson/wiki/Table-of-Content](https://github.com/redisson/redisson/wiki/Table-of-Content)
- [https://hyperconnect.github.io/2019/11/15/redis-distributed-lock-1.html](https://hyperconnect.github.io/2019/11/15/redis-distributed-lock-1.html)