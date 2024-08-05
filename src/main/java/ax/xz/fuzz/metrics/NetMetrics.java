package ax.xz.fuzz.metrics;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NetMetrics implements AutoCloseable {
	private static class ThreadMetrics {
		private long faultedSamples, succeededSamples, alarm, branches, mismatch;
		private final long id;

		private ThreadMetrics(long id) {
			this.id = id;
		}
	}

	private final AtomicInteger idCounter = new AtomicInteger();

	private final ConcurrentHashMap<Integer, ThreadMetrics> metrics = new ConcurrentHashMap<>();
	private final ThreadLocal<ThreadMetrics> metricsLocal = ThreadLocal.withInitial(() -> {
		var id = idCounter.getAndIncrement();
		var metrics = new ThreadMetrics(id);
		this.metrics.put(id, metrics);
		return metrics;
	});

	private final HttpServer server;

	public NetMetrics(InetSocketAddress address) throws IOException {
		this.server = HttpServer.create(address, 10);
	}

	public NetMetrics startServer() {
		if (server != null) {
			server.createContext("/", ex -> {
				var response = getMetrics();
				ex.sendResponseHeaders(200, response.length());
				ex.getResponseBody().write(response.getBytes());
				ex.getResponseBody().close();
				ex.close();
			});
			server.start();
		}

		return this;
	}

	private String getMetrics() {
		var sb = new StringBuilder();
		for (var entry : metrics.entrySet()) {
			int threadId = entry.getKey();
			sb.append("samples_executed_total{thread=\"%d\", faulted=\"true\"} %d%n".formatted(threadId, entry.getValue().faultedSamples));
			sb.append("samples_executed_total{thread=\"%d\", faulted=\"false\"} %d%n".formatted(threadId, entry.getValue().succeededSamples));
			sb.append("timeout{thread=\"%d\"} %d%n".formatted(threadId, entry.getValue().alarm));
			sb.append("mismatches_total{thread=\"%d\"} %d%n".formatted(threadId, entry.getValue().mismatch));
			sb.append("branches_executed_total{thread=\"%d\"} %d%n".formatted(threadId, entry.getValue().branches));
		}

		return sb.toString();
	}

	public synchronized void incNumSamples(boolean faulted) {
		var metrics = metricsLocal.get();
		if (faulted)
			metrics.faultedSamples++;
		else
			metrics.succeededSamples++;
	}

	public synchronized void incrementAlarm() {
		metricsLocal.get().alarm++;
	}

	public void incrementBranches(long n) {
		metricsLocal.get().branches += n;
	}

	public void incrementMismatch() {
		metricsLocal.get().mismatch++;
	}

	@Override
	public void close() {
		server.stop(1);
	}
}
