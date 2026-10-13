# ZenCode · Terminal IDE

离线 Java Web 开发工具，采用 terminal 风格的原生桌面界面，提供代码编辑、传统语义补全和中文排错辅助。

目标环境为 Windows 7 SP1 / Windows 10 x64、4GB 内存和 JDK 8。**当前为开发原型，尚未完成目标系统实机兼容性与性能验收。**

## 当前能力

- Java + Eclipse Platform + JDT + WTP 工程与 Windows x64 产品构建。
- terminal 风格工作台、项目/编辑/输出/助手布局和应用命令栏。
- Java 方法名拼写建议：结合接收者类型、公开方法及参数类型筛选候选。
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

解压产品，在 `ZenCode.ini` 的 `-vmargs` 之前加入两行：第一行 `-vm`，第二行为本机 JDK 8 的 `bin/javaw.exe` 绝对路径。启动 `ZenCode.exe` 并选择可写工作区。

创建或导入 Java 项目，将 `samples/CopyingMistakes.java.txt` 的内容放入对应 Java 类文件：

1. 在 `pritnln` 处按 `Ctrl+1`，查看带 `[ZenCode]` 的快速修复候选。
2. 点击助手中的 `[ CHECK ]`，检查全角语法标点。
3. 输入 `preview 1` 查看第一个建议，输入 `fix 1` 确认应用。
4. 应用后重新检查，或在编辑器中撤销。

命令栏只处理应用命令，不执行系统 shell。标点检查目前仅用于 `.java` 文件；方法拼写适配暂不覆盖隐式接收者、泛型方法和可变参数等复杂情况。

## 验证状态

已通过 14 项核心回归测试、8 项真实 JDT 解析与快速修复场景测试，以及 Windows x64 交叉构建和压缩包检查。

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
