#!/bin/bash

# 设置JAVA_HOME为Java 17
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

# 颜色定义
GREEN="\033[0;32m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
NC="\033[0m" # No Color

# 显示帮助信息
show_help() {
    echo "${GREEN}Android象棋项目构建脚本${NC}"
    echo "使用方法: ./build.sh [选项]"
    echo ""
    echo "选项:"
    echo "  build          构建Debug版本"
    echo "  build-release  构建Release版本"
    echo "  install        构建并安装Debug版本到设备"
    echo "  install-release 构建并安装Release版本到设备"
    echo "  clean          清理构建缓存"
    echo "  test           运行测试"
    echo "  apk            生成Debug APK文件"
    echo "  bundle         生成Android App Bundle"
    echo "  info           显示项目信息"
    echo "  help           显示此帮助信息"
    echo ""
}

# 显示项目信息
show_info() {
    echo "${GREEN}项目信息${NC}"
    echo "Java版本："
    java -version
    echo ""
    echo "Gradle版本："
    ./gradlew -version
    echo ""
    echo "项目变体："
    ./gradlew tasks --group="Build"
}

# 构建Debug版本
build_debug() {
    echo "${YELLOW}开始构建Debug版本...${NC}"
    ./gradlew assembleDebug
    if [ $? -eq 0 ]; then
        echo "${GREEN}Debug版本构建成功！${NC}"
    else
        echo "${RED}Debug版本构建失败！${NC}"
        exit 1
    fi
}

# 构建Release版本
build_release() {
    echo "${YELLOW}开始构建Release版本...${NC}"
    ./gradlew assembleRelease
    if [ $? -eq 0 ]; then
        echo "${GREEN}Release版本构建成功！${NC}"
    else
        echo "${RED}Release版本构建失败！${NC}"
        exit 1
    fi
}

# 安装Debug版本
install_debug() {
    echo "${YELLOW}开始构建并安装Debug版本...${NC}"
    ./gradlew installDebug
    if [ $? -eq 0 ]; then
        echo "${GREEN}Debug版本安装成功！${NC}"
    else
        echo "${RED}Debug版本安装失败！${NC}"
        exit 1
    fi
}

# 安装Release版本
install_release() {
    echo "${YELLOW}开始构建并安装Release版本...${NC}"
    ./gradlew installRelease
    if [ $? -eq 0 ]; then
        echo "${GREEN}Release版本安装成功！${NC}"
    else
        echo "${RED}Release版本安装失败！${NC}"
        exit 1
    fi
}

# 清理构建缓存
clean_build() {
    echo "${YELLOW}清理构建缓存...${NC}"
    ./gradlew clean
    if [ $? -eq 0 ]; then
        echo "${GREEN}构建缓存清理成功！${NC}"
    else
        echo "${RED}构建缓存清理失败！${NC}"
        exit 1
    fi
}

# 运行测试
run_tests() {
    echo "${YELLOW}运行测试...${NC}"
    ./gradlew test
    if [ $? -eq 0 ]; then
        echo "${GREEN}测试运行成功！${NC}"
    else
        echo "${RED}测试运行失败！${NC}"
        exit 1
    fi
}

# 生成APK文件
build_apk() {
    echo "${YELLOW}生成Debug APK文件...${NC}"
    ./gradlew assembleDebug
    if [ $? -eq 0 ]; then
        echo "${GREEN}APK文件生成成功！${NC}"
        echo "APK文件位置: app/build/outputs/apk/debug/app-debug.apk"
    else
        echo "${RED}APK文件生成失败！${NC}"
        exit 1
    fi
}

# 生成AAB文件
build_bundle() {
    echo "${YELLOW}生成Android App Bundle...${NC}"
    ./gradlew bundleDebug
    if [ $? -eq 0 ]; then
        echo "${GREEN}AAB文件生成成功！${NC}"
        echo "AAB文件位置: app/build/outputs/bundle/debug/app-debug.aab"
    else
        echo "${RED}AAB文件生成失败！${NC}"
        exit 1
    fi
}

# 主函数
main() {
    if [ $# -eq 0 ]; then
        # 默认构建Debug版本
        build_debug
    else
        case "$1" in
            build)
                build_debug
                ;;
            build-release)
                build_release
                ;;
            install)
                install_debug
                ;;
            install-release)
                install_release
                ;;
            clean)
                clean_build
                ;;
            test)
                run_tests
                ;;
            apk)
                build_apk
                ;;
            bundle)
                build_bundle
                ;;
            info)
                show_info
                ;;
            help)
                show_help
                ;;
            *)
                echo "${RED}未知选项: $1${NC}"
                echo "使用 ./build.sh help 查看可用选项"
                exit 1
                ;;
        esac
    fi
}

# 执行主函数
main "$@"
