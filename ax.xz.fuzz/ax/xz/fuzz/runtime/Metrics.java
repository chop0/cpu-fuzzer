package ax.xz.fuzz.runtime;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Metrics implements AutoCloseable {
	private final HttpServer server;

	private long opcodeCount, faultedSamples, succeededSamples, samples, branches;

	public Metrics() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(9100), 10);
	}

	public Metrics startServer() {
		server.createContext("/", ex -> {
			var response = getMetrics();
			ex.sendResponseHeaders(200, response.length());
			ex.getResponseBody().write(response.getBytes());
			ex.getResponseBody().close();
			ex.close();
		});
		server.start();

		return this;
	}

	private String getMetrics() {
		return """
				opcode_count %d
				samples_executed_total{faulted="true"} %d
				samples_executed_total{faulted="false"} %d
				branches_executed_total %d
				""".formatted(opcodeCount, faultedSamples, succeededSamples, branches);
	}

	public synchronized void incNumSamples(boolean faulted) {
		samples++;
		if (faulted)
			faultedSamples++;
		else
			succeededSamples++;
	}

	public synchronized void incrementBranches(long n) {
		branches += n;
	}

	public synchronized void setOpcodeCount(long opcodeCount) {
		this.opcodeCount = opcodeCount;
	}

	@Override
	public void close() {
		server.stop(1);
	}
}
