#!/bin/bash
#
# Pikafish Android .so 编译脚本 (Pikafish 2026-01-02)
# 产物:
#   build/android/libpikafish-arm64-v8a.so  (ARM64, NEON)
#   build/android/libpikafish-x86_64.so     (x86_64, AVX2)
#
set -e

export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}"
if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "错误: 请设置 ANDROID_NDK_HOME"
    exit 1
fi

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/darwin-x86_64/bin"
[ ! -d "$NDK_BIN" ] && NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
[ ! -d "$NDK_BIN" ] && { echo "错误: 找不到 NDK 工具链"; exit 1; }
export PATH="$NDK_BIN:$PATH"

PROJECT="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT/src"
OUT="$PROJECT/build/android"
mkdir -p "$OUT"

cd "$SRC"

# ====== 统一 NNUE ======
# 始终以仓库标准网络文件 (Pikafish.2026-01-02/pikafish.nnue) 为准覆盖 src/pikafish.nnue，
# 避免 src 下残留旧版本、或意外触发 net.sh 下载到不一致的网络权重。
STANDARD_NNUE="$PROJECT/../Pikafish.2026-01-02/pikafish.nnue"
if [ -f "$STANDARD_NNUE" ]; then
    cp -f "$STANDARD_NNUE" "$SRC/pikafish.nnue"
    echo "已统一 NNUE <- $(basename "$STANDARD_NNUE")"
else
    [ ! -f pikafish.nnue ] && bash ../scripts/net.sh
fi

JOBS=$(sysctl -n hw.ncpu 2>/dev/null || nproc)
echo "NDK : $ANDROID_NDK_HOME"
echo "Jobs: $JOBS"

# ====== 通用编译标志 ======
BASE_FLAGS="-std=c++17"
BASE_FLAGS="$BASE_FLAGS -fPIC -fno-exceptions -fvisibility=hidden"
BASE_FLAGS="$BASE_FLAGS -Wall -Wcast-qual"
BASE_FLAGS="$BASE_FLAGS -O3 -funroll-loops"
BASE_FLAGS="$BASE_FLAGS -ffunction-sections -fdata-sections"
BASE_FLAGS="$BASE_FLAGS -DNDEBUG -DUSE_PTHREADS -DIS_64BIT"
BASE_FLAGS="$BASE_FLAGS -stdlib=libc++"

# 源文件 (排除 main.cpp)
SRCS=$(find . \( -name '*.cpp' -o -name '*.S' \) -print | sed 's|^\./||' | grep -v '^main\.cpp$')
SRC_COUNT=$(echo "$SRCS" | wc -l | tr -d ' ')
echo "源文件: $SRC_COUNT"

# ====== 编译 + 链接 ======
compile_so() {
    local label="$1" cc="$2" arch_flags="$3" out_name="$4"

    echo ""
    echo "=========================================="
    echo "  编译: $label"
    echo "  CC : $cc"
    echo "=========================================="

    local build_dir="$SRC/build_obj_${out_name%.so}"
    rm -rf "$build_dir"
    mkdir -p "$build_dir"

    local objs=""
    local i=0
    local total=$SRC_COUNT

    for src in $SRCS; do
        i=$((i + 1))

        # ARM64 跳过 x86 专属汇编
        if echo "$cc" | grep -q "aarch64" && echo "$src" | grep -q "amd64.S"; then
            continue
        fi
        # x86_64 跳过潜在的 ARM 汇编
        if echo "$cc" | grep -q "x86_64" && echo "$src" | grep -qE "(aarch64|arm).*\.S"; then
            continue
        fi

        local obj="$build_dir/$(echo "$src" | tr '/' '_').o"
        local extra=""
        [ "$src" = "misc.cpp" ] && extra="-DGIT_SHA=unknown -DGIT_DATE=$(date -u +%Y%m%d)"

        printf "  [%2d/%d] %-55s\r" "$i" "$total" "$src"
        "$cc" $BASE_FLAGS $arch_flags $extra -c "$src" -o "$obj" || {
            echo ""
            echo "✗ 编译失败: $src"
            return 1
        }
        objs="$objs $obj"
    done
    echo ""

    echo "  链接 -> $out_name ..."
    "$cc" -shared $objs -llog -static-libstdc++ \
        -Wl,--gc-sections -Wl,-soname,"$out_name" \
        -o "$OUT/$out_name" || {
        echo "✗ 链接失败"
        return 1
    }

    llvm-strip "$OUT/$out_name" 2>/dev/null || true
    echo "  ✓ $out_name ($(ls -lh "$OUT/$out_name" | awk '{print $5}'))"
}

# ====== 1. ARM64 (arm64-v8a) ======
compile_so \
    "ARM64 (arm64-v8a)" \
    "aarch64-linux-android29-clang++" \
    "-DUSE_POPCNT -DUSE_NEON -DARCH=armv8" \
    "libpikafish-arm64-v8a.so"

# ====== 2. x86_64 ======
compile_so \
    "x86_64" \
    "x86_64-linux-android29-clang++" \
    "-DUSE_POPCNT -DUSE_AVX2 -DUSE_SSE41 -DUSE_SSE2 -DUSE_SSSE3
     -mavx2 -mbmi -msse4.1 -msse2 -mssse3 -msse
     -DARCH=x86-64-avx2" \
    "libpikafish-x86_64.so"

# ====== 清理 ======
rm -rf "$SRC"/build_obj_*

# ====== 汇总 ======
echo ""
echo "=========================================="
echo "  全部完成！"
echo "=========================================="
echo ""
for f in "$OUT"/*.so; do
    [ -f "$f" ] || continue
    printf "  %-40s %-8s %s\n" "$(basename "$f")" "$(ls -lh "$f" | awk '{print $5}')" "$(file -b "$f")"
done
echo ""
echo "导出 JNI 符号:"
for f in "$OUT"/*.so; do
    [ -f "$f" ] || continue
    echo "  $(basename "$f"):"
    nm -D "$f" 2>/dev/null | grep Java_ | sed 's/^/    /' || echo "    (无符号)"
done
echo ""

# ====== 复制到项目对应目录 (app/src/main/jniLibs) ======
# 编译产物名为 libpikafish-<arch>.so，Android 按 jniLibs/<abi>/libpikafish.so 加载，
# 这里重命名为 libpikafish.so 并放入对应 ABI 子目录。
APP_JNILIBS="$PROJECT/../app/src/main/jniLibs"
mkdir -p "$APP_JNILIBS/arm64-v8a" "$APP_JNILIBS/x86_64"

if [ -f "$OUT/libpikafish-arm64-v8a.so" ]; then
    cp -f "$OUT/libpikafish-arm64-v8a.so" "$APP_JNILIBS/arm64-v8a/libpikafish.so"
    echo "  ✓ 已复制 arm64-v8a -> app/src/main/jniLibs/arm64-v8a/libpikafish.so"
fi
if [ -f "$OUT/libpikafish-x86_64.so" ]; then
    cp -f "$OUT/libpikafish-x86_64.so" "$APP_JNILIBS/x86_64/libpikafish.so"
    echo "  ✓ 已复制 x86_64   -> app/src/main/jniLibs/x86_64/libpikafish.so"
fi
echo ""
