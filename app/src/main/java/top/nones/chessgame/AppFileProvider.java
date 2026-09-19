package top.nones.chessgame;

import androidx.core.content.FileProvider;

/**
 * App 自身使用的 FileProvider 子类。
 * 与 registration-lib.aar 内同样基于 androidx.core.content.FileProvider 的 provider 区分开来，
 * 避免 Manifest 合并时因 android:name 相同而被当作同一组件导致 authorities/resource 冲突。
 * 不重写任何逻辑，仅用于拥有独立的组件类名。
 */
public class AppFileProvider extends FileProvider {
}
