# ZenCode · Terminal IDE

离线 Java Web 开发工具，采用 terminal 风格的原生桌面界面，提供代码编辑、传统语义补全和中文排错辅助。

目标环境为 Windows 7 SP1 / Windows 10 x64、4GB 内存和 JDK 8。**当前为开发原型，尚未完成目标系统实机兼容性与性能验收。**

## 0.9.0 模块与验收

- Web 示例工程：通过 WTP 向导选择 runtime/JDK 8，再按实际源码和部署目录加入 JSP、Servlet、JDBC 示例；拒绝覆盖已有源码。也支持导出纯目录模板。
- 服务器：提供配置、运行和调试入口，复用 WTP 生命周期；本地安装检查识别 Tomcat 7.0、8.0、8.5、9.0。
- Web 检查：后台读取当前编辑快照，检查静态重复 id、相对资源路径、明确链接的本地 CSS 名称候选，汇总已保存文件的 Eclipse/WTP 诊断。支持选择结果定位，文件变化后拒绝过期定位。
- 表单/JSP：用户显式确认候选配对后，比较静态表单名称与 JSP scriptlet AST 中的字符串取参名称；有限 JSP scriptlet 上下文使用项目 JDT 类型绑定确认 Servlet API；绑定失败时降级为候选建议，不自动修改。web.xml 精确路由可关联唯一 Servlet 源文件。
- 数据库：多 JAR 本地驱动、常见配置模板、Java 8 校验、后台连接、只读表列元数据、SQL/Java/JSP 编辑器表列候选插入（Ctrl+Alt+Space，支持撤销与快照校验）、参数化查询示例。15秒停止等待，可取消；不执行用户 SQL、不保存凭据。

检查不是完整的浏览器或 JSP 执行环境。动态路径、动态样式、完整 JSP 类型绑定纠错和现代 JavaScript 支持仍有边界；MF 对应产品未确认。Windows、实际数据库、JSP断点及四版本服务器仍需统一实测，不能将构建通过视为验收通过。

离线 Tomcat 冒烟脚本（需要已有 JDK 8、对应 Tomcat 安装目录）：

```sh
python3 scripts/smoke-tomcat.py --java-home /path/to/jdk8 /path/to/tomcat7 /path/to/tomcat8 /path/to/tomcat85 /path/to/tomcat9
```

脚本使用独立临时服务器目录，只管理自己启动的进程，验证 Servlet 响应与 JSP 编译，不替代 IDE 调试实测。

## 当前能力

- Java + Eclipse Platform + JDT + WTP 工程与 Windows x64 产品构建。
- terminal 风格工作台，支持深色和浅色模式并保存选择。
- 常驻“ZenCode 工具”顶栏菜单，可随时重新打开排错面板、查看帮助或恢复 Java 默认字体。
- 未设置 Java 字体的工作区首次初始化时使用系统默认字体；保留已有字体选择。
- 自有界面中文化，内置 Eclipse 平台、Java/Web 工具和 EMF 的简体中文语言包。
- Java 方法名拼写建议：结合接收者类型、公开方法及参数类型筛选候选。
- `check` 汇总当前 Java 内容的编译诊断、项目构建路径错误及部分结构风险（字符串比较、数组边界、空 catch、finally 返回和 SQL 拼接等）；风险建议不自动修改，不能替代业务逻辑审查。
- 全角语法标点检查：跳过字符串、字符字面量和注释。
- 修改预览、确认应用及编辑器撤销；源码变化后拒绝过期修复。

HTML、CSS、JavaScript、JSP、SQL 辅助、Tomcat 多版本运行、数据库连接与跨文件诊断是整体开发范围。其中 WTP 已纳入产品依赖，完整运行流程和各版本适配仍需逐项实现与验证。

## 构建

准备 Maven 3.9.x、JDK 21 和 Python 3。首次构建需要访问 Maven Central 与 Eclipse p2；产品运行目标 JDK 8 与构建环境分开配置。

```sh
# 核心回归测试
mvn -B verify

# 编译并打包 Windows x64 产品
mvn -B -f releng/pom.xml verify

# 检查产物和 JDT 集成
python3 scripts/verify-product.py
python3 scripts/test-jdt-integration.py
```

Windows 中可将 `python3` 替换为 `python`。

产物位于 `products/cn.zencode.product/target/products/`。当前压缩包不包含 JDK、Tomcat 服务器和 JDBC 驱动。

## 运行与试用

Windows 启动入口为 **`ZenCode.exe`**。请使用完整产品包 `cn.zencode.product-win32.win32.x86_64.zip`；`target` 或 `plugins` 中的 JAR 是组件包，不能通过双击独立启动。

1. 将整个 ZIP 解压到本地目录，保留 `plugins`、`features`、`configuration` 等目录；不要直接在压缩软件中启动，也不要只复制 EXE 或 JAR。
2. 准备 **64 位 JDK 8**。Windows 是 64 位不代表已安装的 Java 也是 64 位。
3. 在 `ZenCode.ini` 的 `-vmargs` **之前**加入以下两行，将示例路径替换为实际路径（INI 中路径不加引号）：

   ```text
   -vm
   C:\Java\jdk8\bin\javaw.exe
   ```

4. 双击 `ZenCode.exe`，选择可写工作区。

如果 EXE 也启动失败，在解压目录的命令提示符中运行下面的命令，并将 Java 路径替换为实际路径。这次使用 `java.exe` 以显示诊断输出：

```bat
ZenCode.exe -vm "C:\Java\jdk8\bin\java.exe" -consoleLog
```

排查时保留弹窗的完整错误内容、`configuration` 下最新的 `.log` 文件，以及所选工作区的 `.metadata/.log`（如果已经生成）。这些信息可以区分 Java 路径或位数问题、组件加载失败和工作区错误。

创建或导入 Java 项目，将 `samples/CopyingMistakes.java.txt` 的内容放入对应 Java 类文件：

1. 在 `pritnln` 处按 `Ctrl+1`，查看带 `[ZenCode]` 的快速修复候选。
2. 点击助手中的 `[ 检查 ]`，查看编译诊断、风险提示和标点建议。
3. 输入 `preview 1` 查看第一个建议，输入 `fix 1` 确认应用。
4. 应用后重新检查，或在编辑器中撤销。

命令栏只处理应用命令，不执行系统 shell。标点检查目前仅用于 `.java` 文件；方法拼写适配暂不覆盖隐式接收者、泛型方法和可变参数等复杂情况。

## 外观与面板恢复

顶栏 **ZenCode 工具** 提供“打开离线排错”“使用帮助与恢复面板”“深色模式”和“浅色模式”。关闭排错面板后，可从该菜单重新打开；菜单不依赖面板是否打开，也不受当前透视图影响。

命令栏支持 `帮助` / `help`、`检查` / `check`、`预览 1` / `preview 1`、`修复 1` / `fix 1`、`清空` / `clear`。帮助包含重新打开面板的路径。

产品默认使用简体中文。语言包随压缩包提供，无需运行时下载；上游未翻译的条目可能仍显示原文。更新时请完整解压到新目录，保留并选择原工作区，避免混合不同版本的插件。首次打开新版本会迁移旧的自定义主题设置。

## 验证状态

已通过 41 项核心回归测试、8 项真实 JDT 解析与快速修复场景测试，以及 Windows x64 交叉构建和压缩包检查。

这些结果不等同于 Windows 实机验证。界面视觉、中文输入、JDK 8 实际运行、Java/JSP 调试、多版本服务器、断网部署及 4GB 性能仍待验收。

## 源码结构

```text
bundles/cn.zencode.assist.core/  独立纠错核心
bundles/cn.zencode.ide/          原生工作台与 JDT 适配
features/                      产品组件集合
products/                      产品定义
releng/                        Tycho 构建入口
tests/                         核心回归测试
integration-tests/             JDT 集成测试
samples/                       示例与反例
scripts/                       产物验证工具
```

测试普通类时请使用与公开类同名的文件，例如 `BadCode.java`，不要写入 `module-info.java`。若出现 JRE 系统库未绑定，请在项目的 Java 构建路径中选择有效的 JDK 8。

## Windows 测试安装包

安装包包含配套 JDK 8，支持离线安装，默认安装到当前用户目录，并创建桌面和开始菜单快捷方式。快捷方式使用“文档”目录下的 `ZenCodeWorkspace`；卸载保留工作区。安装目标目录必须为空，不覆盖已有版本。

先完成 Windows 产品构建，再准备 Temurin `OpenJDK8U-jdk_x64_windows_hotspot_8u362b09.zip`，安装 NSIS 后运行：

```sh
python3 scripts/build-installer.py --jdk-zip /path/to/OpenJDK8U-jdk_x64_windows_hotspot_8u362b09.zip
```

脚本验证固定 JDK 校验值，产物及 SHA-256 文件位于 `dist/windows-installer/`。此 JDK 为当前兼容性测试基线。安装包为未签名测试版本，安装、启动和卸载仍需在目标 Windows 环境验收。

## 0.9.0 测试材料与边界

测试代码、正反例及15组逐步操作位于 samples/acceptance-0.9.0；安装包同时附带“测试用例”目录。
已在 macOS ARM、Zulu JDK 8u504 上实际启动 Tomcat 7.0.109、8.0.53、8.5.100、9.0.98，验证仓库模板的 JSP 编译、注解 Servlet HTTP 响应及 JDBC 示例编译。这不包含数据库连接或 Windows IDE 调试。

尚未完成：完整 JSP 转译/include/自定义标签语义、注解及通配 Servlet 路由关联、泛型/可变参数自有拼写修复、完整现代 JS 分析、SQL执行与结果分页、数据库各厂商真实连接矩阵、通用离线模板包导入、Windows 目标设备完整验收。MF产品身份仍未确认。0.9.0为可测试版本，并不代表总体大纲所有项目完成。

## 0.9.0 汉化修订

新增 WTP 工程/Servlet 向导简体中文补充资源，覆盖项目配置、目标运行环境、Java 源目录、Web 内容目录、上下文根路径与部署描述文件。补充资源随产品离线提供，未匹配的条目继续使用原语言包。可运行 python3 scripts/test-web-localization.py 验证资源键、占位符和中文加载；Windows 页面布局仍需实测。
