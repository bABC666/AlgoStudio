package com.algostudio.model.benchmark;

/** 仅保存运行环境，不采集用户名、路径、网络或设备标识。 */
public record BenchmarkEnvironment(String javaVersion, String vm, String os, String architecture, int processors) {
    public static BenchmarkEnvironment current() {
        return new BenchmarkEnvironment(System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
    }
}
