package ax.xz.fuzz.runtime;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.InetSocketAddress;

public class Metrics implements AutoCloseable {
	private static final VarHandle BRANCHES, FAULTED_SAMPLES, OPCODE_COUNT, SAMPLES, SUCCEEDED_SAMPLES;

	static {
		try {
			var lookup = MethodHandles.lookup();
			BRANCHES = lookup.findVarHandle(Metrics.class, "branches", long.class);
			FAULTED_SAMPLES = lookup.findVarHandle(Metrics.class, "faultedSamples", long.class);
			OPCODE_COUNT = lookup.findVarHandle(Metrics.class, "opcodeCount", long.class);
			SAMPLES = lookup.findVarHandle(Metrics.class, "samples", long.class);
			SUCCEEDED_SAMPLES = lookup.findVarHandle(Metrics.class, "succeededSamples", long.class);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new AssertionError(e);
		}
	}

	private final HttpServer server;

	private volatile long opcodeCount, faultedSamples, succeededSamples, samples, branches;

	public Metrics() throws IOException {
		HttpServer server1;
		try {
			server1 = HttpServer.create(new InetSocketAddress(9100), 10);
		} catch (IOException e) {
			new IOException("Failed to create server", e).printStackTrace();
			server1 = null;
		}
		this.server = server1;
	}

	public Metrics startServer() {
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
		return """
			opcode_count %d
			samples_executed_total{faulted="true"} %d
			samples_executed_total{faulted="false"} %d
			branches_executed_total %d
			""".formatted((long) OPCODE_COUNT.get(this), (long) FAULTED_SAMPLES.get(this), (long) SUCCEEDED_SAMPLES.get(this), (long) BRANCHES.get(this));
	}

	public synchronized void incNumSamples(boolean faulted) {
		SAMPLES.getAndAdd(this, 1);
		if (faulted)
			FAULTED_SAMPLES.getAndAdd(this, 1);
		else
			SUCCEEDED_SAMPLES.getAndAdd(this, 1);
	}

	public void incrementBranches(long n) {
		BRANCHES.getAndAdd(this, n);
	}

	@Override
	public void close() {
		server.stop(1);
	}
}
