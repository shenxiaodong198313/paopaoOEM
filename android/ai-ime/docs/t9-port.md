# ai-ime T9 移植说明

## 背景

本次改动在分支 `t9-rebase-midscene-1.8.8` 中，将
`C:\Users\15811\Downloads\fcitx5-android-t9-phone`
里的纯 T9 Kotlin 代码按最小接入原则移植到当前 `ai-ime`。

目标是优先引入：

- `input.t9` 下的纯 Kotlin 模型与工具类
- `T9Keyboard` 九键布局入口
- `KeyboardWindow` / `InputView` / `FcitxInputMethodService` 的必要接入点

同时保留：

- `FcitxInputMethodService` 作为宿主可继续继承的 `open class`
- 现有 `InputMethodUtil` 宿主桥接实现

## 已引入内容

- 新增 `org.fcitx.fcitx5.android.input.t9` 下的纯 Kotlin 文件：
  - `T9CompositionModel.kt`
  - `T9CompositionTracker.kt`
  - `T9PinyinUtils.kt`
  - `T9CandidateBudget.kt`
- 新增 `T9Keyboard.kt`，提供单行 T9 键盘布局
- 新增 `KeyAction.T9ModeSwitchAction`
- 在 `CommonKeyActionListener` 中接入 T9 模式切换动作
- 在 `FcitxInputMethodService` 中加入最小 T9 模式状态：
  - `getCurrentT9ModeLabel()`
  - `switchToNextT9Mode()`
  - `onT9ModeChanged`
- 在 `FcitxInputMethodService` 中补齐中文 T9 主链第一版：
  - 数字串状态机与已选拼音分段缓存
  - 数字串到拼音候选的预算裁剪
  - 删除键优先回退数字串，再回退最近一段已选拼音
  - 模式切换时清空 T9 中文中间态，避免污染普通输入
- 在 `CandidatesView` 中加入 T9 覆盖态：
  - 有未决数字串时优先展示数字串/拼音候选
  - 点击拼音候选后回写服务层并重建 `fcitx` 预编辑串
  - 无未决数字串时自动退回原生 `fcitx` 候选显示
- 在 `KeyboardWindow` 中注册 T9 布局并支持默认主键盘切到 `T9`
- 在 `InputView` 中根据当前窗口在普通高度和 T9 高度之间切换
- 在 `AppPrefs` 中加入 `use_t9_keyboard_layout` 和 `t9_keyboard_height`

## 当前边界

本次移植已经从“纯 Kotlin 文件 + 必要入口接线”推进到“服务层状态机 +
候选视图覆盖”的第一版，但还没有整包迁移参考项目中对
`FcitxInputMethodService`、`CandidatesView`、状态栏和密码临时全键盘的大规模改造。

当前已具备：

- T9 键盘布局入口和模式标签切换显示（中 / En / 123）
- T9 独立高度切换
- 中文 T9 数字串状态机
- 数字串到拼音候选覆盖展示
- 拼音候选点击后与 `fcitx` 中文候选联动
- 删除回退到数字串/上一个已选拼音分段
- 模式切换时清理中文 T9 中间态

当前未完成：

- 真正完整的九键字母主键区布局仍未整体迁入
- `CandidatesView` 里的 T9 覆盖态目前只有单页拼音候选，没有做扩展分页
- 尚未补齐参考项目里的密码临时全键盘、状态栏联动等附加链路
- 参考项目中的密码临时全键盘与相关状态栏控制

## 后续建议

若要继续完成完整中文 T9 行为，下一阶段建议按以下顺序推进：

1. 完整迁入真正的九键字母主键区布局和数字键行为
2. 在 `CandidatesView` 中补齐 T9 拼音条、候选预算分页与分页操作
3. 评估是否需要一并移植 `InputUiFont`、`T9PinyinChipAdapter` 等 UI 依赖
4. 最后再决定是否并入参考项目中的密码临时全键盘链路
