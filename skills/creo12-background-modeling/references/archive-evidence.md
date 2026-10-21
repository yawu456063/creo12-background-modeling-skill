# 封存证据

日期：2026-10-21。原始验证环境：Windows，Creo 12.4.0.0，Java 21，ECJ 3.37.0（Java 8 字节码），本机有效许可。

原始第四版独立重开、再生验证通过：11 个零件，11 个已固定，11 个定位矩阵吻合，干涉 0，原生 SHELL 1 个、DRAFT 3 个。原始日志保存于 `assets/models/verification.txt`。预览由实际 STL 三角形生成，配色未写入原生外观。

本次封装将 Java 中机器专属输出路径替换为环境变量，并提供编译/启动脚本。封装后的回调已在独立目录实际运行 Build，再于第二会话 Verify 通过；新建模和重开记录见 `evidence/packaged-build-log.txt` 与 `evidence/packaged-verification.txt`。全部 11 个部件、固定及放置矩阵检查通过，干涉 0。预览与射线脚本也已在新输出目录运行通过。其他环境仍需重新验证，不能依赖封存日志代替本次运行。

书本依据记录在 model-review.md，只包含必要的概括和页码。不随仓库分发用户提供的教材 PDF、PTC 软件、otk.jar、Java 运行时或 ECJ。
