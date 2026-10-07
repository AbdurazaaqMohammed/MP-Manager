# AI 评审规则

评审这个仓库的 PR 时，除通用 Android/Java 经验外，请特别注意下面这些约定。它们是本项目
的实际做法，不是风格偏好；违反其中任何一条都应当作为「阻塞」或「建议」指出。

## 构建与验证

- 本地没有 Android SDK，**无法本地编译**，只能靠 CI（工作流 `Build APK`）。因此提交里的
  代码必须自身经得起编译：新增调用先确认被调用方的真实签名（javap / 源码），不要凭记忆写
  API 名称。历史上多次因为记错签名（`getUncompressedSize()`、`List.length`）导致编译失败。
- 绿色标准不只是 `build`：还要 `Verify signing identity` 这一步为 success。只看 job 结论不够。
- 大改动（多文件、重构）应拆成可独立验证的提交。

## 语言与 API 水平

- minSdk 21 + desugaring，所以 `java.util.function.Predicate`、`java.util.stream` 等是**可用**
  的；但不要引入需要 API 24+ 且没有 desugar 覆盖的写法。
- 项目使用 Java 16 模式匹配（`if (o instanceof Foo f)`），新代码沿用这一风格。
- 语言字符串必须同时提供 `values/`、`values-zh-rCN/`、`values-ru/`；只加英文会让中文用户看到
  key 而不是文案。

## 压缩包相关（改动高发区）

- 写入安全：`ArchiveUtil.canWriteBack()` 决定哪些格式允许写回。给 zip/7z/rar/tar 增加写操作前，
  必须先确认该格式的写入库真的支持，否则应当像现有代码那样**提前拒绝并提示**，绝不能静默丢弃
  修改或写出损坏文件。
- 加密：目前只有 zip 支持加密创建（zip4j AES）；7z/rar/tar 只读不解密写入。密码错误必须与
  「文件损坏」区分开，并保证重试循环不会吞掉用户输入。
- 密码处理：按「已记住 → 空 → 已存密码 → 询问」的顺序；取消询问要删除已建目录；取消解压要
  保留已完成文件；任何密码或密钥都不要写进日志。
- 进度对话框是单例复用的（`ProgressManager`），异步流程结束时必须 `dismiss()`，异常路径也要。
- 暂存文件（解压/压缩条目时的 cache 目录）必须在 `finally` 里清理，包括用户取消与异常。

## 压缩包内条目操作

- 菜单回调是异步的：展示菜单到用户点击之间，列表可能被替换。必须重新解析行位置并丢弃失效的
  选中项，命中不了就提示重试——否则会删掉用户没在看的那一行。
- 写入 zip 条目时保存结果依赖 intent 里的 `zf` / `zipEntryPath`，回写失败要有兜底路径。

## 通用

- 线程：耗时操作一律进 `new Thread(...)`，UI 更新回主线程 handler；不要在主线程做 IO。
- 文件操作要考虑 root / Shizuku / 普通三种路径能力差异（`AccessManager`、`ShizukuFileOps`）。
- 不要把 `/tmp` 当作可用目录（CI 与部分环境不可写）。
- 新增功能后，如果 README 描述了它，也要同步更新；尚未实现的功能不要写进功能介绍，单列在
  「未实现的功能」一节。