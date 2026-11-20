# Zero Symbol Input View V2

V2 是一个独立集成的 Android 符号快捷栏与编辑辅助工具栏库，提供符号分组、分页指示器、抽屉展开、短按/长按动作、符号管理、设置、数据导入导出和扩展 API。

**核心设计：V2 不依赖任何具体编辑器内核。** 核心模块不依赖 Sora Editor、MT 管理器或宿主应用的编辑器对象。宿主只需实现一次 SymbolActionHost 适配器；需要复用通用文本命令时，再实现 SymbolTextDocument，即可把 V2 动作接入自己的编辑器。

- 不需要修改或 fork 编辑器源码。
- 不要求编辑器对象继承 V2 类型。
- 不要求宿主使用 Sora API。
- 可接入自研编辑器、Sora、EditText、WebView 编辑器或其它文本组件。
- Sora 等特定编辑器适配器应放在宿主或独立 adapter module，不应成为 V2 核心依赖。

旧 V1 模块仍保留。V2 使用独立存储空间；旧数据迁移为只读导入，不会回写 V1 偏好数据。

## 目录

1. [快速接入本地 Module](#1-快速接入本地-module)
2. [JitPack 远程 Maven 导入](#2-jitpack-远程-maven-导入)
3. [布局绑定控件](#3-布局绑定控件)
4. [最小接入代码](#4-最小接入代码)
5. [任意编辑器接入与 Sora 解耦](#5-任意编辑器接入与-sora-解耦)
6. [通用文本编辑动作](#6-通用文本编辑动作)
7. [动作扩展与隐藏内置功能](#7-动作扩展与隐藏内置功能)
8. [自定义指示器、设置和主题](#8-自定义指示器设置和主题)
9. [管理界面和设置界面](#9-管理界面和设置界面)
10. [MT 管理器 / V1 JSON 互通](#10-mt-管理器--v1-json-互通)
11. [存储与迁移](#11-存储与迁移)
12. [API 文档](#12-api-文档)

## 1. 快速接入本地 Module

如果把本仓库作为宿主项目的一部分，在根目录 settings.gradle.kts 中包含：

~~~kotlin
include(":zero-symbol-input-view-v2")
~~~

在 app 模块中添加：

~~~kotlin
dependencies {
    implementation(project(":zero-symbol-input-view-v2"))
}
~~~

模块目录是仓库根目录下的 zero-symbol-input-view-v2/。

## 2. JitPack 远程 Maven 导入

V2 是独立 Maven artifact。消费者不需要下载源码，也不需要把 module 复制到自己的工程。

### 2.1 添加 Maven 仓库

如果项目使用集中式仓库管理，在根目录 settings.gradle.kts 的 dependencyResolutionManagement.repositories 中添加：

~~~kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
~~~

该依赖使用仓库级聚合坐标 `com.github.msmt2018:zero-Symbol-input-view:4.1-v2`，适用于当前 `4.1-v2` 标签对应的仓库聚合发布。不要将它误写成旧文档中的 `v0.25.0`，也不要把 V2 子模块的 Maven 坐标与根项目聚合坐标混为一谈。JitPack 对多模块项目的根 artifact 与单独模块 artifact 使用不同坐标规则，见 [JitPack multi-module projects](https://docs.jitpack.io/building/#multi-module-projects)。

Groovy DSL：

~~~groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
~~~

如果项目设置了 FAIL_ON_PROJECT_REPOS，必须在 settings.gradle(.kts) 中添加 JitPack，而不是只在 app/build.gradle 中配置。

### 2.2 添加依赖

Kotlin DSL：

~~~kotlin
dependencies {
    implementation("com.github.msmt2018:zero-Symbol-input-view:4.1-v2")
}
~~~

Groovy DSL：

~~~groovy
dependencies {
    implementation 'com.github.msmt2018:zero-Symbol-input-view:4.1-v2'
}
~~~

将 4.1-v2 替换为仓库中已经发布且 JitPack 构建成功的 Git Tag。不要把未发布分支当作正式版本。

发布坐标：

| 项目 | 值 |
|---|---|
| Maven repository | https://jitpack.io |
| Group | com.github.msmt2018 |
| Artifact | zero-symbol-input-view-v2 |
| Version | Git Tag，例如 4.1-v2 |
| 完整坐标 | com.github.msmt2018:zero-Symbol-input-view:4.1-v2 |

仓库维护者通过 GitHub Actions → Publish V2 library to JitPack 手动发布，输入不带 v 的版本号（例如 4.1-v2）。工作流会创建 4.1-v2 tag、运行 V2 单元测试、构建 Release AAR / Sources JAR 并验证 Maven 发布。普通 Android CI 不会自动发布 Maven 版本。公开仓库的消费者不需要 Maven 账号、密码或 GitHub Token。

## xedit-app 实际接入示例（4.1-v2）

当前 xedit-app 的启动入口 `MainActivity` 只负责存储权限和跳转；真正持有编辑器的是 `x.code.app.activity.EditorActivity`，编辑器 View 是 `HighlightTextView`，旧快捷键栏是 `shortcutKeyView`。因此符号控件应接入 EditorActivity，而不是权限/启动页 MainActivity。

app 模块依赖：
```kotlin
implementation("com.github.msmt2018:zero-Symbol-input-view:4.1-v2")
```

根目录 `settings.gradle.kts` 已启用 JitPack，并使用 `FAIL_ON_PROJECT_REPOS` 集中管理仓库，不需要在 app 模块重复声明仓库。

在 `activity_editor.xml` 中保留旧快捷键 RecyclerView，并与 V2 控件放入同一个纵向底部容器：
```xml
<LinearLayout
    android:id="@+id/bottom_toolbars"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    android:orientation="vertical">
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/shortcut_key_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
    <com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView
        android:id="@+id/symbol_drawer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```

在 `EditorActivity.onCreate()` 中绑定动作并加载分组。xedit-app 当前已确认的编辑器写入 API 是 `binding.editor.insert(text)`：
```kotlin
binding.symbolDrawer.apply {
    onSymbolActionListener =
        SymbolDrawerView.OnSymbolActionListener { symbol, isLongPress ->
            val action = if (isLongPress) symbol.longAction else symbol.shortAction
            if (action != null) {
                val result = executeAction(action, editorSymbolActionHost())
                // 根据 SymbolActionResult 记录不支持/失败动作
            }
        }
    reloadGroups()
}
```

最小宿主适配器将 `SymbolActionHost.insertText()` 映射到 `HighlightTextView.insert()`。选区替换、光标移动、删除、剪贴板、编辑器命令和扩展动作必须在确认编辑器 API 后逐项实现；未实现时返回 `false`，不能伪报成功。当前接入先打通符号插入路径。

编辑器可视区域底部留白应覆盖两个底部控件的总高度：
```kotlin
bottomToolbars.post {
    editor.setMarginLayout(bottom = bottomToolbars.height)
}
```

`MainActivity` 是启动/权限页面，不应在该页面创建编辑器符号控件。Fragment/Activity 生命周期结束时应解除动作回调和扩展注册句柄。

## xedit-app 实际接入和完整 API import

本仓库提供针对 `msmt2018/xedit-app` main 分支的真实接入记录，包含依赖、布局、`EditorActivity`、`HighlightTextView` 插入适配、类型 import 和能力边界：

- [xedit-app 4.1-v2 实际接入指南](api-docs/18-xedit-app-integration.md)

注意 xedit-app 的 `MainActivity` 是启动/权限页面，真正的编辑器位于 `EditorActivity`。
## 3. 布局绑定控件

### 3.1 XML

~~~xml
<com.zerostudio.idea.widget.editor.symbolinput.v2.SymbolDrawerView
    android:id="@+id/symbolDrawer"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
~~~

把控件放在编辑器下方或你自己的底部容器中。V2 控件内部负责符号网格、分组指示器和横向分页；宿主负责页面整体布局、窗口 Insets 和编辑器区域。

### 3.2 Activity

~~~kotlin
class EditorActivity : AppCompatActivity() {
    private lateinit var drawer: SymbolDrawerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        drawer = findViewById(R.id.symbolDrawer)
        drawer.onOpenManagerListener = { openSymbolManager() }
        drawer.onOpenSettingsListener = { openSymbolSettings() }
        drawer.reloadGroups()
    }
}
~~~

### 3.3 Fragment

~~~kotlin
class EditorFragment : Fragment(R.layout.fragment_editor) {
    private var drawer: SymbolDrawerView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        drawer = view.findViewById(R.id.symbolDrawer)
        drawer?.reloadGroups()
    }

    override fun onDestroyView() {
        drawer?.onSymbolActionListener = null
        drawer?.onOpenManagerListener = null
        drawer?.onOpenSettingsListener = null
        drawer = null
        super.onDestroyView()
    }
}
~~~

也可以纯代码创建：

~~~kotlin
val drawer = SymbolDrawerView(context)
container.addView(
    drawer,
    ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
)
~~~

## 4. 最小接入代码

符号点击只产生动作请求；真正的文本修改由宿主实现的 SymbolActionHost 完成。

~~~kotlin
private val actionExecutor = SymbolActionExecutor()
private lateinit var editorActionHost: SymbolActionHost

private fun bindDrawer(drawer: SymbolDrawerView) {
    editorActionHost = createMyEditorActionHost()

    drawer.onSymbolActionListener =
        SymbolDrawerView.OnSymbolActionListener { symbol, isLongPress ->
            val action = if (isLongPress) symbol.longAction else symbol.shortAction
            if (action != null) {
                val host = drawer.bindActionHost(editorActionHost)
                when (val result = actionExecutor.execute(action, host)) {
                    SymbolActionResult.Executed -> Unit
                    is SymbolActionResult.Unsupported -> showMessage("编辑器不支持该动作")
                    is SymbolActionResult.Invalid -> showMessage(result.reason)
                    is SymbolActionResult.Failed -> reportError(result.cause)
                    is SymbolActionResult.Cancelled -> Unit
                    is SymbolActionResult.PartialFailure -> reportPartialFailure(result)
                }
            }
        }

    drawer.onSymbolUsedListener = { symbol, isLongPress ->
        // 可选：更新宿主自己的最近使用记录或统计数据。
    }
}
~~~

SymbolActionExecutor 的结果是结构化类型：Executed、Unsupported、Invalid、Failed、Cancelled、PartialFailure。Composite 动作不是自动事务；如需整组原子回滚，应由宿主编辑器提供事务/撤销组。

## 5. 任意编辑器接入与 Sora 解耦

### 5.1 架构边界

~~~text
宿主应用
├── 自己的编辑器核心 / Editor View
├── EditorActionAdapter（宿主实现）
│   ├── SymbolActionHost
│   └── SymbolTextDocument（可选）
└── Zero Symbol Input View V2
    ├── SymbolDrawerView
    ├── SymbolAction / SymbolActionExecutor
    ├── SymbolGroup / SymbolEntry
    └── Manager / Settings / Extension APIs
~~~

V2 核心 API 不暴露 Sora 的 CodeEditor、Content、Cursor 等类型。使用 Sora 的应用在宿主侧实现 SoraAdapter；使用自研编辑器的应用实现自己的 Adapter。两个应用共享同一个 V2 AAR，不需要共享编辑器源码。

### 5.2 SymbolActionHost

这是 V2 与编辑器之间的通用命令边界：

~~~kotlin
interface SymbolActionHost {
    fun insertText(template: String): Boolean
    fun replaceSelection(text: String): Boolean
    fun surroundSelection(prefix: String, suffix: String): Boolean
    fun moveCursor(target: CursorTarget): Boolean
    fun delete(kind: DeleteKind): Boolean
    fun transformSelection(transform: SelectionTransform): Boolean
    fun executeEditorCommand(commandId: String, payload: String?): Boolean
    fun performClipboard(operation: ClipboardOperation): Boolean
    fun openManager(): Boolean
    fun executeExtension(extensionId: String, payload: String?): Boolean
}
~~~

每个方法返回 true 表示动作已被宿主接受并完成；false 表示当前不支持或状态不允许。执行器捕获执行期间抛出的 Exception 并返回 Failed。调用是同步的，默认运行在调用线程；如果编辑器要求主线程访问，宿主应在主线程调用 execute，不要异步投递后立即返回 true。

### 5.3 Adapter 结构示例

下面的 MyEditor 是宿主自己的编辑器类型，不是 V2 类。方法名需要映射到你的真实编辑器 API：

~~~kotlin
class MyEditorActionHost(
    private val editor: MyEditor,
    private val clipboard: ClipboardGateway,
    private val openManagerPage: () -> Unit
) : SymbolActionHost {

    override fun insertText(template: String): Boolean {
        if (!editor.isEditable) return false
        editor.commitText(template)
        return true
    }

    override fun replaceSelection(text: String): Boolean {
        if (!editor.isEditable) return false
        editor.replaceRange(editor.selectionStart, editor.selectionEnd, text)
        return true
    }

    override fun surroundSelection(prefix: String, suffix: String): Boolean {
        if (!editor.isEditable) return false
        val selected = editor.selectedText ?: return false
        editor.replaceSelection(prefix + selected + suffix)
        return true
    }

    override fun moveCursor(target: CursorTarget): Boolean =
        editor.moveCursor(target)

    override fun delete(kind: DeleteKind): Boolean =
        editor.delete(kind)

    override fun transformSelection(transform: SelectionTransform): Boolean =
        editor.transformSelection(transform)

    override fun executeEditorCommand(commandId: String, payload: String?): Boolean =
        editor.executeCommand(commandId, payload)

    override fun performClipboard(operation: ClipboardOperation): Boolean =
        clipboard.perform(editor, operation)

    override fun openManager(): Boolean {
        openManagerPage()
        return true
    }

    override fun executeExtension(extensionId: String, payload: String?): Boolean =
        false
}
~~~

### 5.4 SymbolTextDocument：复用通用文本算法

如果编辑器能提供文本、行、光标和选区的基础能力，建议额外实现 SymbolTextDocument。它使用 UTF-16 offset，范围为左闭右开 [start, end)：

~~~kotlin
interface SymbolTextDocument {
    val isEditable: Boolean
    val textLength: Int
    val lineCount: Int
    val cursorOffset: Int
    val selectionStart: Int
    val selectionEnd: Int

    fun lineForOffset(offset: Int): Int
    fun lineStartOffset(line: Int): Int
    fun lineEndOffset(line: Int): Int
    fun lineText(line: Int): String
    fun textBetween(start: Int, end: Int): String
    fun charAt(offset: Int): Char
    fun replace(start: Int, end: Int, replacement: String): Boolean
    fun setSelection(start: Int, end: Int)
}
~~~

这样通用文本动作算法只写一次，Sora、自研编辑器和其它宿主分别实现适配器即可。GenericSymbolTextActions.createRegistry() 提供通用命令 handler 集；宿主可以把这些 handler 接入自己的 executeEditorCommand 分发逻辑。复杂编辑器命令仍可直接映射到宿主已有 API。

如果你希望直接使用 V2 已提供的通用动作宿主，不想自己再实现 SymbolActionHost，可以使用 SymbolTextDocumentActionHost：

~~~kotlin
val actionHost = SymbolTextDocumentActionHost(
    document = myEditorDocumentAdapter,
    clipboard = SymbolTextClipboard { document, operation ->
        myClipboard.perform(document, operation)
    },
    openManagerAction = {
        openMySymbolManager()
        true
    }
)

val result = SymbolActionExecutor().execute(symbol.shortAction, drawer.bindActionHost(actionHost))
~~~

它会自动把 executeEditorCommand 映射到 GenericSymbolTextActions 注册的 builtin 命令，并提供插入宏、选区替换/包围、光标移动、删除和大小写转换。剪贴板、管理器导航和编辑器专属命令仍由宿主注入。

### 5.5 是否需要绑定编辑器核心对象？

需要的是**绑定一个宿主 Adapter**，不是绑定某个编辑器核心类型。V2 不会自动猜测任意编辑器的 API；宿主必须实现一次接口，把插入、删除、选区、光标、剪贴板和自定义命令映射到自己的编辑器。完成后，V2 的符号数据、管理界面、动作模型和 UI 都不再关心底层编辑器是什么。

## 6. 通用文本编辑动作

GenericSymbolTextActions 提供常用命令族：

- 行：复制、删除、清空、替换、上下插入空行、合并、上下移动、选择、大小写转换、缩进/反缩进、注释。
- 选区：删除、复制、大小写转换、括号/引号/标签包围、缩进/反缩进、反转行、排序行、注释。
- 光标：按单词移动/删除、选择单词/行、行首/行尾、文档首尾及扩展选择。
- 文档：全选、清理行首/行尾空白、确保末尾换行、删除空白行。
- 通用插入：insert_payload。

完整 command ID 和 payload 约定见 api-docs/11-interaction-and-builtins.md。文档级全量操作限制为 32 MiB 文本长度和 200,000 行；超过限制时拒绝执行，避免无边界扫描。

## 7. 动作扩展与隐藏内置功能

每个 SymbolDrawerView 提供独立的扩展注册表：

~~~kotlin
val extensions = drawer.extensions
~~~

### 7.1 注册自定义动作

~~~kotlin
val actionHandle = drawer.extensions.registerAction(
    SymbolDrawerActionExtension(
        id = "myeditor.formatSelection",
        title = "格式化选区",
        description = "调用宿主自己的格式化 API",
        payloadHint = "可选格式化参数",
        priority = 100,
        handler = SymbolActionHandler { host, payload ->
            host.executeEditorCommand("myeditor.formatSelection", payload)
        }
    )
)
~~~

符号动作使用稳定 ID：

~~~kotlin
SymbolAction.Extension("myeditor.formatSelection", null)
~~~

自定义动作会自动进入 drawer.extensions.actions() 列表；drawer.bindActionHost(host) 会让扩展动作通过注册表分发。相同 ID 再注册会替换当前 handler，旧句柄关闭时不会误删新注册。

### 7.2 隐藏或恢复内置功能

~~~kotlin
drawer.extensions.setBuiltInVisible("builtin.search", false)
drawer.extensions.setBuiltInVisible("builtin.favorites", false)

// 恢复显示
drawer.extensions.setBuiltInVisible("builtin.search", true)
~~~

隐藏不是删除源码，也不破坏数据。它改变宿主功能列表的运行时可见性。应用应使用稳定 feature ID，并在需要跨重启保存时自行持久化 builtInVisibilitySnapshot()。

## 8. 自定义指示器、设置和主题

扩展注册表会保留自定义贡献并按 priority 排序，宿主设置 UI 可以读取列表生成选择项。

### 8.1 自定义分组指示器

实现 SymbolGroupIndicatorExtension：

~~~kotlin
drawer.extensions.registerIndicator(
    object : SymbolGroupIndicatorExtension {
        override val id = "myeditor.timeline"
        override val title = "时间轴指示器"
        override val priority = 100

        override fun create(
            context: Context,
            initialState: SymbolGroupIndicatorState
        ): SymbolGroupIndicatorComponent {
            return MyTimelineIndicator(context, initialState)
        }
    }
)
~~~

SymbolGroupIndicatorComponent 需要提供 view、submitGroups(groups)、setSelectedIndex(index)、applySettings(settings)。指示器只负责显示和发出选中索引，不拥有分组数据，也不能创建另一套 Pager；选中时调用 initialState.onSelectGroup(index)，由 V2 的分页状态统一切换。

读取动态列表：drawer.extensions.indicators()。

### 8.2 添加自定义设置 Tab

~~~kotlin
drawer.extensions.registerSettingsTab(
    object : SymbolDrawerSettingsTabExtension {
        override val id = "myeditor.advanced"
        override val title = "编辑器高级设置"
        override val priority = 100

        override fun createView(
            context: Context,
            scope: SymbolDrawerSettingsScope
        ): View = MyAdvancedSettingsView(context, scope)
    }
)
~~~

### 8.3 向已有 Tab 添加自定义设置项

~~~kotlin
drawer.extensions.registerSetting(
    object : SymbolDrawerSettingExtension {
        override val id = "myeditor.autoPair"
        override val tabId = "editor"
        override val title = "自动补全括号"
        override val priority = 50

        override fun createView(
            context: Context,
            scope: SymbolDrawerSettingsScope
        ): View = MyAutoPairPreference(context)
    }
)
~~~

SymbolDrawerSettingsScope.current 提供当前 V2 设置快照；scope.update { old -> old.copy(...) } 更新通用设置。自定义设置值也可以由宿主单独存储，不必塞进 V2 的固定设置 schema。

### 8.4 自定义主题和全局主题

~~~kotlin
drawer.extensions.registerTheme(
    object : SymbolDrawerThemeExtension {
        override val id = "myeditor.midnight"
        override val title = "Midnight"

        override fun resolve(
            context: Context,
            settings: SymbolDrawerSettings
        ) = SymbolDrawerTheme(
            primary = 0xFF80CBC4.toInt(),
            onPrimary = 0xFF00201D.toInt(),
            surface = 0xFF101820.toInt(),
            onSurface = 0xFFE0F2F1.toInt(),
            secondaryText = 0xFF9BB7B3.toInt(),
            outline = 0xFF526B67.toInt()
        )
    }
)
~~~

读取 drawer.extensions.themes() 生成主题选项。宿主可以选一个全局主题，也可以分别应用到工具栏、设置页和管理页。Theme provider 返回颜色 token，不应擅自修改宿主 Activity 的 Window 或系统栏。

### 8.5 注册生命周期与关闭

每个 register 方法返回 AutoCloseable：

~~~kotlin
val handles = mutableListOf<AutoCloseable>()
handles += drawer.extensions.registerAction(...)
handles += drawer.extensions.registerIndicator(...)
handles += drawer.extensions.registerSettingsTab(...)
handles += drawer.extensions.registerSetting(...)
handles += drawer.extensions.registerTheme(...)

// 扩展或编辑器会话结束时
handles.forEach { it.close() }
handles.clear()
~~~

也可以调用 drawer.extensions.clear() 清除该 Drawer 的全部扩展。不要在 Activity 重建时重复注册而不关闭旧句柄。注册对象和 lambda 不会序列化；持久化稳定 ID 和用户配置，重建后重新注册。

## 9. 管理界面和设置界面

### 9.1 提交符号分组

~~~kotlin
drawer.submitGroups(
    listOf(
        SymbolGroup(
            id = "kotlin",
            title = "Kotlin",
            symbols = listOf(
                SymbolEntry(
                    id = 1L,
                    label = "fun",
                    shortAction = SymbolAction.InsertText("fun ")
                ),
                SymbolEntry(
                    id = 2L,
                    label = "if",
                    shortAction = SymbolAction.InsertText("if () {\n    \n}")
                )
            )
        )
    )
)
~~~

Group ID 和 Symbol ID 应保持稳定；刷新数据时不要每次随机生成 ID。

### 9.2 内置管理界面

~~~kotlin
val manager = SymbolDrawerManagerScreen(context)
val binding = manager.bindToDrawer(drawer)
manager.onGroupsChanged = { groups -> /* 宿主同步 */ }
manager.onError = { error -> reportError(error) }

// 页面退出时
binding.close()
~~~

Manager 提供分组/符号创建、编辑、删除、排序、复制/移动、批量操作、搜索、收藏、最近使用和 JSON 备份导入导出。

### 9.3 内置设置界面

~~~kotlin
val settings = SymbolDrawerSettingsScreen(context)
settings.onSettingsChanged = drawer::applySettings
settings.onError = { error -> reportError(error) }
drawer.applySettings(settings.getSettings())
container.addView(settings)
~~~

设置包含列数、固定显示行数、单元格尺寸/间距、指示器风格、符号标签、完整标题、展开状态记忆、最大展开高度、动画、长按时间、触觉反馈、缓存、触控目标和大屏停靠等选项。

## 10. MT 管理器 / V1 JSON 互通

V2 保留旧版紧凑 JSON 格式的严格解码器和编码器。兼容结构是 JSON 数组：

~~~json
[
  {
    "n": "常用",
    "d": [
      {
        "a": 0,
        "b": "括号",
        "c": "()",
        "d": 0,
        "e": "[]"
      }
    ]
  }
]
~~~

字段：

| 字段 | 含义 |
|---|---|
| 分组 n | 分组名称 |
| 分组 d | 符号项目数组 |
| 项目 a | 短按动作数字 ID |
| 项目 b | 符号显示文本 |
| 项目 c | 短按动作文本参数（可选） |
| 项目 d | 长按动作数字 ID（可选） |
| 项目 e | 长按动作文本参数（可选） |

### 10.1 导入旧格式 JSON

如果 MT 管理器导出的文件采用上述 V1 紧凑结构，可以直接导入，不需要手动改字段：

~~~kotlin
val json = readTextFromDocumentUri(uri)
manager.importMtManagerJson(json)
~~~

通用入口 manager.importBundle(json) 也会识别旧版数组格式并交给 LegacySymbolDataCodec 严格解析。导入前校验结构、动作 ID、字段类型和大小限制；完整解析成功后才替换数据，管理界面提供撤销恢复。

### 10.2 导出旧格式 JSON

~~~kotlin
val legacyJson = manager.exportMtManagerJson()
writeTextToDocumentUri(uri, legacyJson)
~~~

此方法输出旧版紧凑结构，用于 V1 / 兼容该格式的 MT 管理器符号快捷栏交换数据。

### 10.3 兼容边界

旧格式只支持旧数字动作目录能表达的动作。V2 Composite、部分 Selection 操作和任意 Extension 动作不能无损写入 V1 JSON。遇到无法表达的 V2 动作时，exportMtManagerJson() 会拒绝导出并抛出异常，不会静默丢弃动作或伪造数字 ID。

- 旧格式互通：使用 importMtManagerJson / exportMtManagerJson。
- V2 完整备份：使用 exportBundle / importBundle，保留稳定 ID、图标和所有 typed actions。
- 若某个 MT 管理器版本导出的 JSON 与上述 n/d/a/b/c/d/e 结构不同，需要提供一份脱敏样例才能验证并增加对应格式适配；不能把未知结构说成已验证兼容。

## 11. 存储与迁移

默认分组仓储使用独立 SharedPreferences：
- 文件：symbol_drawer_v2
- key：groups_json_v2

V2 新数据使用版本化 typed-action JSON envelope，保存稳定 Group/Symbol ID、图标、短按/长按动作和 V1 兼容元数据。

首次启动时，如果 V2 仓储为空，会尝试读取旧版：
- Preferences：advanced_symbol_prefs
- Key：symbol_json_data

迁移只读旧数据，成功后写入 V2 独立仓储，不会修改或删除旧版 SharedPreferences。V2 不会持续双向同步 V1。

需要 Room、数据库或云同步时，可实现 SymbolGroupRepository 并注入 SymbolGroupManager / SymbolDrawerManagerScreen。

## 12. API 文档

完整开发文档位于 [api-docs/](api-docs/README.md)。建议按以下顺序阅读：

1. [快速开始与集成](api-docs/01-quick-start.md)：依赖、View 创建、数据提交和宿主接入。
2. [SymbolDrawerView API](api-docs/02-symbol-drawer-view.md)：原符号控件的布局、状态、回调、分页和抽屉行为。
3. [模型与动作](api-docs/03-models-and-actions.md)：分组、符号、typed action 和序列化模型。
4. [动作运行时](api-docs/04-action-runtime.md)：SymbolActionHost、Executor、Registry、结果、取消和复合动作。
5. [数据与管理](api-docs/05-data-and-management.md)、[设置 API](api-docs/06-settings.md)：仓储、管理界面、导入导出和设置更新。
6. [通用 Drawer](api-docs/07-universal-drawer.md)、[导航/面板/样式](api-docs/08-navigation-panels-style.md)：功能中立抽屉框架。
7. [自适应窗口](api-docs/09-adaptive-window.md)、[兼容性索引](api-docs/10-api-index-compatibility.md)：大屏布局、公共 API 和迁移边界。
8. [交互与内置动作](api-docs/11-interaction-and-builtins.md)：手势和内置文本命令。
9. [扩展注册框架](api-docs/12-extension-framework.md)：注册表、扩展贡献和真实 UI 消费者。
10. [公共包路径与编辑器中立文档契约](api-docs/13-public-package-and-editor-neutral-actions.md)。
11. [通用 Drawer 公共 API 参考](api-docs/14-drawer-public-api-reference.md)。
12. [符号控件扩展 API 实战参考](api-docs/15-symbol-control-extension-api.md)。
13. [测试、发布、兼容性与故障排查](api-docs/16-testing-release-troubleshooting.md)。

### 扩展开发的正确路径

扩展必须依附当前 `SymbolDrawerView` 实例的 `extensions` 注册表，并由原控件消费者处理。不要另建第二套符号列表、Pager、手势识别器或数据仓库。

| 开发目标 | 注册入口 | 真实消费方 |
|---|---|---|
| 新增短按/长按动作 | `registerAction` / `registerLegacyAction` | `SymbolDrawerManagerScreen` + 原 `SymbolActionExecutor` |
| 自定义符号单元格 | `registerItemView` | 原 `SymbolPageAdapter` |
| 替换分组导航指示器 | `registerIndicator` + `setCustomGroupIndicator` | `SymbolDrawerView` 原指示器插槽 |
| 增加设置 Tab/行 | `registerSettingsTab` / `registerSetting` | `SymbolDrawerSettingsScreen` |
| 自定义颜色主题 | `registerTheme` + `setThemeExtension` | 已接入的 Drawer 外壳、网格和指示器表面 |
| 提供旧版分组 | `registerGroupProvider` | 原 Legacy codec + Repository 管线 |
| 观察控件事件 | `registerLifecycle` | 当前 Drawer session |

注册句柄必须由 Fragment View 或 Editor Session 持有并关闭。注册的 handler/provider 不会写入 JSON；进程重建后由宿主重新注册。扩展 View 不能接管原控件的分页、滚动、触摸和动作执行所有权。

### API 稳定性提示

`com.zerostudio.idea.widget.drawer` 是新公共 Kotlin 入口；部分类型目前仍通过 Kotlin `typealias` 暴露。Kotlin 源码可以导入这些 facade，但 Java 不能把 typealias 当作独立类。符号输入产品 API 仍位于 `com.zerostudio.idea.widget.editor.symbolinput.v2`。不要仅凭新包路径推断所有实现文件已物理迁移。

## Public drawer API package and editor-neutral actions

New Kotlin integrations should import the reusable drawer API from `com.zerostudio.idea.widget.drawer` rather than coupling their code to the symbol-input feature package:

```kotlin
import com.zerostudio.idea.widget.drawer.DrawerContainer
import com.zerostudio.idea.widget.drawer.core.DrawerController
import com.zerostudio.idea.widget.drawer.navigation.DrawerPageHost
import com.zerostudio.idea.widget.drawer.action.SymbolTextDocument
import com.zerostudio.idea.widget.drawer.action.SymbolTextDocumentActionHost
import com.zerostudio.idea.widget.drawer.extension.SymbolDrawerExtensionRegistry
```

The generic drawer package is separate from the symbol-input product namespace. The current release branch exposes the new package through Kotlin type-alias facades while the implementation sources are being relocated; Java source cannot use Kotlin type aliases as Java classes. See [API package migration and editor-neutral document contract](api-docs/13-public-package-and-editor-neutral-actions.md) for the exact compatibility boundary.

### No editor-core dependency

V2 action execution does not require a Sora `CodeEditor`, a custom editor class, an editor View, or an editor-core instance. The host implements `SymbolTextDocument` for the active text buffer. It exposes UTF-16 text ranges, line/column mapping, caret/selection, and replace operations. The action host then executes built-in operations against that contract.

```kotlin
val document: SymbolTextDocument = MyBufferAdapter(myTextBuffer)
val actionHost = SymbolTextDocumentActionHost(document)
val boundHost = drawer.bindActionHost(actionHost)
```

`MyBufferAdapter` is application-owned and only translates the target editor's buffer operations into the document contract. It does not pass the editor object into V2. Clipboard, manager navigation and app-specific commands are injected as optional host capabilities.

The document contract also provides line/column conversion, selection access, optional edit transaction hooks and right-to-left multi-range edits. Cursor-left/right and adjacent code-point deletion read only the UTF-16 code units around the caret rather than copying the full document.

### Live extension snapshots

`SymbolDrawerExtensionRegistry.observe { snapshot -> ... }` provides lifecycle-managed change notifications. `snapshot()` returns immutable sorted contribution lists for actions, indicators, settings tabs/items, themes and built-in visibility overrides. Close the returned observer handle when the owning screen/view is disposed.

The observer API enables host UI surfaces to render contributions dynamically. In the current V2 implementation, custom actions are consumed by the manager action picker when it receives the same registry; custom indicators are consumed by the SymbolDrawerView indicator slot; settings tabs/items are consumed by SymbolDrawerSettingsScreen when it receives the same registry; item renderers are consumed by the existing SymbolPageAdapter. Theme application is connected to the drawer shell, page adapter and indicator surfaces described in the extension API reference. These are concrete consumer paths, not a promise that every screen color or every future built-in feature is already theme-aware.

## API documentation / API 文档

- [English API documentation](api-docs/en/README.md)
- [简体中文 API documentation](api-docs/README.md)
