# 连接 Creo、设计零件与后台运行

## 方案和边界

本方案实际验证的版本是 Creo 12.4.0.0，Windows；其他 Creo 12 维护版本需重新检查 API 与许可兼容性。无需 Creo 窗口在前台，但必须具备启动独立 Creo 会话及相关接口所需的许可。后台会话仍占用 CPU、内存和许可。

执行链路为：Codex 生成/修改 Java → 编译引用本机 otk.jar → 在独立工作目录生成 config.pro 和 creotk.dat → 隐藏启动 parametric.exe → Creo 自动装载同步 Java 回调 → 回调取 Session、建特征、再生、保存 → 第二独立会话重开验证。

这套实现连接的是专门启动的后台会话，不会自动附着或控制用户已经打开的 Creo 窗口。外部异步连接不是本案例验证成功的路径。

## 安装和依赖

将技能目录复制到 `%USERPROFILE%\.codex\skills\creo12-background-modeling`，重新打开 Codex 会话。可输入：

> 使用 $creo12-background-modeling 在后台修改吨吨杯，把杯身高度调整为……，保存为新版本并重开验证。

需要确认以下路径：

| 参数 | 本次验证的本机示例 |
|---|---|
| CreoRoot | `D:\cero12.0\Creo 12.4.0.0` |
| Java | `D:\Bianma\CREOCODEX\third_party\CreosonServerWithSetup-3.0.0-win64\jre\bin\java.exe`（Java 21） |
| EcjJar | `D:\Bianma\CREOCODEX\third_party\ecj-3.37.0.jar` |

Java 运行时被放在 Creoson 解压目录是本机历史布局；本方案不依赖 Creoson 服务。ECJ 使用 3.37.0，编译目标为 Java 8。其他编译器或运行时需单独适配。本仓库不自动下载或分发这些依赖。

## 可直接执行的命令

在 PowerShell 中先按自己的安装位置设定路径。RunDirectory 必须不存在；脚本不会清除旧目录。

```powershell
$skillPath = Join-Path $env:USERPROFILE '.codex\skills\creo12-background-modeling'
$common = @{
  CreoRoot = 'D:\cero12.0\Creo 12.4.0.0'
  Java = 'D:\Bianma\CREOCODEX\third_party\CreosonServerWithSetup-3.0.0-win64\jre\bin\java.exe'
  EcjJar = 'D:\Bianma\CREOCODEX\third_party\ecj-3.37.0.jar'
}
# 验证封存模型：去掉 -Launch 可只编译和生成配置，不启动 Creo。
& "$skillPath\scripts\Invoke-CreoModel.ps1" @common `
  -Mode Verify -RunDirectory 'D:\CreoRuns\verify-001' -Launch

# 重新生成杯盖、提手等案例零件，杯身从封存模型复制为基准。
& "$skillPath\scripts\Invoke-CreoModel.ps1" @common `
  -Mode Build -RunDirectory 'D:\CreoRuns\build-001' -Launch

# 等 build-001/models/build-log.txt 出现 PASS BUILD 后，
# 结束该自有会话，再使用另一个会话重开这次生成的文件。
& "$skillPath\scripts\Invoke-CreoModel.ps1" @common `
  -Mode Verify -RunDirectory 'D:\CreoRuns\verify-002' `
  -ModelDirectory 'D:\CreoRuns\build-001\models' -Launch
```

启动脚本返回的 parentProcessId 是启动器 PID；Creo 的实际计算进程为 xtop.exe。启动器退出不代表计算结束。查看 `session.json`、build-log.txt 或 verification.txt 判断进度；等待超过约 3 分钟仍无日志时检查当前目录 std.err、注册文件、Java 路径、许可和进程状态。无 PASS 就不能声称成功。

回调完成后不自动退出 Creo。确认日志完成后，仅关闭该次独立会话。根据 session.json 的启动器 PID 和当前 xtop.exe 的 CommandLine 中 `-ppid <PID>` 一起核对，同时检查可执行文件属于指定 Creo 12 安装路径。不能按进程名批量终止，也不要停止其他版本、nmsd 或 Portmapper 服务。若无法确认进程归属，保留会话并报告。

## Java 注册细节

脚本生成的核心注册字段为：

```text
startup otk_java
toolkit object
creo_type parametric
java_app_class MoldCupV4
java_app_start start
java_app_stop stop
java_app_classpath <run>/classes
allow_stop true
delay_start false
text_dir <run>/text
```

config.pro 指定 jlink_java_command 和 toolkit_registry_file。启动参数为 `"<CreoRoot>\Parametric\bin\parametric.psf" -g:no_graphics -i:rpc_input`。本案例保留完整安装路径与空格；特殊字符或非 ASCII 运行路径需要在目标环境验证，脚本的注册文件按 ASCII 写入，建议运行目录使用 ASCII 路径。

Java 入口中使用：

```java
Session s = pfcSession.GetCurrentSessionWithCompatibility(CreoCompatibility.C4Compatible);
```

编译使用安装目录 `Common Files/text/java/otk.jar`；运行时自建 classpath 只放自己的编译类，让 Creo 加载自身 API。输出位置由启动脚本给子进程设置 `CREO_MODEL_OUTPUT`，父进程环境随后恢复。

## 新零件如何设计

先确定尺寸及设计意图，再选择旋转、拉伸、孔、壳、拔模等原生特征。案例中的 NativeExactBody 用封闭截面旋转；NativeDetailHandle 创建提手；NativeMountBand 创建带安装耳的抱箍和减胶孔；NativeMoldOps 增加原生壳与拔模。

修改杯子尺寸时应同时修改剖面、配合件及装配位置。当前 Build 回调保留原始杯身基准，若需改变杯身必须显式修改其创建逻辑并更新验证目标，不能仅改杯盖后声称整杯参数化完成。新的任意零件仍需要编写相应 Java 回调，本技能不是将任何自然语言自动转换成几何的通用引擎。

先 WCreateFeature 创建未完成特征，填入草绘，再 RedefineFeature 完成。自动标注可能将小斜角变成竖直约束；本案例使用两点直线样条保持目标轮廓，再用独立旋转体积公式核对实际体积。不要为让检查通过随意放宽误差。

Creo 可能重置首特征草绘原点，修改草绘也可能使局部锚点改变。装配补偿应来自实际几何/轴心，而不能照搬旧坐标。原生装配以 Y 为高度；预览坐标另有变换。不要直接把 STL 坐标当成全局装配坐标。

固定约束建立后重查完整放置矩阵、固定状态和干涉。第一零件默认放置及约束顺序可能导致再生后位置变化，不能只看保存前画面。

## 验证与交付

原生验证回调检查正体积、杯身体积约 67709.699265 mm³、11 个部件与放置矩阵、约束、SHELL/DRAFT 数量及全局干涉，并导出 STL。预期结尾：

```text
INTERFERENCES 0
MOLD_FEATURES shells=1 drafts=3
PASS REOPENED_REGENERATED components=11 fixed=11 placements=11 interferences=0
```

改了设计就应同步修改验证目标并保留理由；新增/删除零件后，11 个部件等目标不再通用。当前杯身模型表示双壁结构，尚未拆成实际拉伸内外胆的工艺零件。模型检查不能替代密封、承重、跌落、模流与试模。

交付必须含整个原生文件集。另存新版本时保留原基线，不把 .asm 单独拷出后遗漏其引用零件。


## 生成预览和有限范围出模筛查

验证回调会导出实际 STL。安装 Python 的 numpy、Pillow 后，可在 Verify 输出目录上运行：

```powershell
python "$skillPath/scripts/render_mesh_preview.py" 'D:/CreoRuns/verify-002/models'
python "$skillPath/scripts/mesh_pull_test.py" 'D:/CreoRuns/verify-002/models'
```

前者从真实网格和放置清单生成模型预览，中文字体使用 Windows 的 msyh.ttc；后者以 0.12 mm 间距沿案例提手/抱箍的局部拉伸轴筛查多段材料射线。修改零件名称或开模方向后需要同步适配筛查，不能直接推广到所有零件。输出不构成可出模认证。
