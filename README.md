# AlgoStudio

AlgoStudio 是一个 JavaFX 算法可视化与排序性能分析工具。每次运行生成一组可回看的状态帧，界面同步展示数据结构、操作计数和伪代码。播放器支持暂停、单步、回退和 1×、2×、4×、8× 变速。

- 排序：冒泡、快速、归并。
- 图算法：广度优先搜索（BFS）、深度优先搜索（DFS）、Dijkstra 最短路径。
- 动态规划：0/1 背包、最长公共子序列（LCS）。
- 性能对比：为各排序算法复制相同的随机输入，分别预热两次、测量五次，并展示中位耗时及操作计数。

## 运行

安装 JDK 21，将 `JAVA_HOME` 设为 JDK 目录。首次构建需要下载 Maven 和项目依赖。

Windows PowerShell：

```powershell
.\mvnw.cmd test
.\mvnw.cmd javafx:run
```

Linux 或 macOS：

```sh
./mvnw test
./mvnw javafx:run
```

## 工程结构

- `src/main/java/com/algostudio/`：算法、轨迹、播放器、JavaFX 界面。
- `src/main/resources/`：界面样式。
- `src/test/java/`：算法对照测试、播放状态测试与 JavaFX 界面集成测试。
