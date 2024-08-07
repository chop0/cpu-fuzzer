package ax.xz.fuzz.x86.arch;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ObjectPool<T> {
	private final Supplier<T> construct;
	private final Consumer<T> reset;
	private final ArrayBlockingQueue<T> pool;

	private ObjectPool(Supplier<T> construct, Consumer<T> reset, ArrayBlockingQueue<T> pool) {
		this.construct = construct;
		this.reset = reset;
		this.pool = pool;
	}

	public static <T> ObjectPool<T> create(Supplier<T> construct, Consumer<T> reset, int size) {
		ArrayBlockingQueue<T> pool = new ArrayBlockingQueue<>(size);
		return new ObjectPool<>(construct, reset, pool);
	}

	public T get() {
		T obj = pool.poll();
		if (obj == null) {
			obj = construct.get();
		}

		reset.accept(obj);
		return obj;
	}

	public void put(T obj) {
		pool.offer(obj);
	}
}
