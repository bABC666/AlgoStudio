package com.algostudio;

import com.algostudio.model.benchmark.BenchmarkRequest;
import com.algostudio.service.BenchmarkService;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式运行生成真实测量证据；不设跨硬件阈值，不使用测试时钟。 */
class BenchmarkEvidenceIT {
    @Test void recordsActualFiveSamplesCountsAndEnvironmentForEachRequiredSize() throws Exception {
        List<String> csv = new ArrayList<>();
        csv.add("algorithm,n,seed,input,warmups,measurements,ns1,ns2,ns3,ns4,ns5,median_ns,comparisons,writes,swaps");
        String environment = "";
        for (int size : List.of(100, 500, 2000)) {
            var report = new BenchmarkService().run(new BenchmarkRequest(size, BenchmarkRequest.DEFAULT_SEED));
            environment = report.environment().toString();
            for (var result : report.results()) {
                assertEquals(5, result.samplesNanos().size());
                assertTrue(result.metrics().comparisons() > 0);
                String samples = String.join(",", result.samplesNanos().stream().map(Object::toString).toList());
                String row = result.algorithm().id() + "," + size + "," + report.request().seed()
                        + ",uniform-int-1-10000,2,5," + samples + "," + result.medianNanos()
                        + "," + result.metrics().comparisons() + "," + result.metrics().writes() + "," + result.metrics().swaps();
                csv.add(row); System.out.println(row);
            }
        }
        Path directory = Path.of("target", "benchmark"); Files.createDirectories(directory);
        Files.write(directory.resolve("measurements.csv"), csv);
        Files.writeString(directory.resolve("environment.txt"), environment + System.lineSeparator()
                + "System.nanoTime; same core + operation counters; NoOpTraceSink; 2 warmups + 5 measured; clones and validation outside timing.\n"
                + "Teaching reference only; no JMH or scientific microbenchmark claim.\n");
    }
}
