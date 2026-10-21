---
name: creo12-background-modeling
description: 在 Windows 上通过 Creo 12 同步 Object TOOLKIT Java 后台会话创建、修改和验证原生零件与装配；适用于需要避免占用鼠标键盘的 Creo 建模，附吨吨杯完整案例。
---

# Creo 12 后台原生建模

先读 [references/workflow.md](references/workflow.md)，按当前任务选择复用完整模型、修改杯子案例或开发新的零件回调。此技能需要用户已安装并具备可用许可的 Creo 12；不包含 PTC 软件、接口库或许可。

## 工作方式

1. 确定形状、主要尺寸、单位、材料/工艺及交付范围。可由既有对话确定的选择不重复询问。
2. 检查本机 Creo、Java、编译器与现有进程。使用独立工作目录和独立无图形 Creo 会话，避免接管用户可见窗口。不要停止用户会话或为解决 RPC 错误停止其他版本的 Creo、nmsd、Portmapper。
3. 编译同步 Java 回调，以 `startup otk_java`、`toolkit object` 注册，由 Creo 自动调用 `start()`。通过 `GetCurrentSessionWithCompatibility(C4Compatible)` 获取当前后台会话，运行时不把 otk.jar 加到自建类路径。
4. 原生特征创建后再生、保存，并核对实际几何。草绘自动约束和局部原点可能改变目标形状，体积为正不等于尺寸正确。
5. 用另一个独立会话重开最终文件，核对体积/尺寸、零件数量、定位和干涉。日志只有完成所有检查并写出 PASS 才算通过；启动进程成功不代表建模成功。
6. 交付完整原生文件集、验证日志、源代码和实际模型预览，说明剩余工程限制。

## 资源

- `assets/models/`：已验证的第四版 11 零件与总装。打开 `dundun_cup_mold_v4_final.asm.1`，保留所有零件在同一目录。原生 Y 为高度方向。
- `assets/java/`：建模与独立验证回调，以及旋转、提手、密封圈、抱箍、抽壳与拔模的实现。输出目录从 `CREO_MODEL_OUTPUT` 环境变量读取。
- [scripts/Invoke-CreoModel.ps1](scripts/Invoke-CreoModel.ps1)：编译并准备/启动案例 Build 或 Verify 会话。参数显式指定本机依赖。默认仅准备；传 `-Launch` 才启动。
- `scripts/render_mesh_preview.py` 与 `scripts/mesh_pull_test.py`：从验证导出的网格生成预览、进行案例局部方向的离散筛查；需要 Python、numpy、Pillow。
- [references/workflow.md](references/workflow.md)：连接方案、命令、适配零件、常见失败与会话清理。
- [references/model-review.md](references/model-review.md)：第四版尺寸、修改步骤、出模风险及验证边界。
- [references/archive-evidence.md](references/archive-evidence.md)：封存版本与验证证据。

此案例的固定约束保持零件当前位置，不是完整的装配配合设计。零干涉和离散射线筛查不能证明密封、夹紧、强度或可量产出模。晒纹需按实际纹理深度、材料和抽拔方向重新核定拔模。
