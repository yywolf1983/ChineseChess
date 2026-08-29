/*
 * Pikafish JNI Interface (adapted for Pikafish 2026-01-02)
 */

#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <sstream>
#include <thread>
#include <vector>
#include <set>
#include <cstdlib>
#include <climits>
#include <android/log.h>

#include "bitboard.h"
#include "position.h"
#include "tune.h"
#include "uci.h"
#include "engine.h"
#include "search.h"

#define LOG_TAG "PikafishJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

using namespace Stockfish;

// StartFEN is defined in engine.cpp (not in a header), declare here
constexpr auto StartFEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w";

static std::unique_ptr<Engine> g_engine = nullptr;
static std::mutex g_mutex;
static std::condition_variable g_cv;
static std::queue<std::string> g_output_queue;
static bool g_engine_initialized = false;
static bool g_engine_running = true;
static bool g_is_searching = false;
static bool g_nnue_loaded = false;
static bool g_initializing = false;

// Options that DON'T exist in this engine version (silently ignore them)
static const std::set<std::string> UNSUPPORTED_OPTIONS = {
    "Contempt", "Skill Level"
};

void output_callback(const std::string& str) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_output_queue.push(str);
    g_cv.notify_all();
}

std::vector<std::string> split_string(const std::string& s, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(s);
    while (std::getline(tokenStream, token, delimiter)) {
        if (!token.empty()) {
            tokens.push_back(token);
        }
    }
    return tokens;
}

// Safe integer parsing without exceptions (compatible with -fno-exceptions)
bool safe_stoi(const std::string& s, int& result) {
    char* end = nullptr;
    long val = std::strtol(s.c_str(), &end, 10);
    if (end == s.c_str() || *end != '\0' || val < INT_MIN || val > INT_MAX) {
        return false;
    }
    result = static_cast<int>(val);
    return true;
}

bool safe_stoll(const std::string& s, int64_t& result) {
    char* end = nullptr;
    long long val = std::strtoll(s.c_str(), &end, 10);
    if (end == s.c_str() || *end != '\0') {
        return false;
    }
    result = static_cast<int64_t>(val);
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_AICore_PikafishAI_nativeInit(JNIEnv* env, jobject thiz, jstring nnuePath, jstring libPath) {
    // 阶段1：加锁检查状态，防止与 quit/nativeCleanup 并发
    std::unique_lock<std::mutex> lock(g_mutex);

    if (g_engine_initialized && g_engine) {
        LOGI("Engine already initialized, reusing");
        return JNI_TRUE;
    }

    // 如果引擎已被 quit 清理，重置状态
    if (g_engine_initialized && !g_engine) {
        LOGW("Engine marked initialized but g_engine is null, re-initializing");
        g_engine_initialized = false;
        g_engine_running = false;
        g_nnue_loaded = false;
        g_is_searching = false;
    }

    // 防止并发初始化：如果已有另一个 nativeInit 在进行中，等待它完成
    while (g_initializing) {
        g_cv.wait(lock);
        if (g_engine_initialized && g_engine) {
            LOGI("Engine initialized by another thread, reusing");
            return JNI_TRUE;
        }
    }

    // 标记初始化中，阻止并发的 quit/cleanup
    g_initializing = true;
    lock.unlock();

    const char* nnue = env->GetStringUTFChars(nnuePath, nullptr);
    std::string nnue_path(nnue);
    env->ReleaseStringUTFChars(nnuePath, nnue);

    LOGI("Initializing Pikafish with NNUE path: %s", nnue_path.c_str());

    Bitboards::init();
    Position::init();

    // Engine constructor: loads NNUE from binaryDirectory + EvalFile
    // binaryDirectory = dirname(nnue_path), EvalFile = "pikafish.nnue"
    LOGI("Creating Engine...");
    std::unique_ptr<Engine> engine = std::make_unique<Engine>(nnue_path);
    LOGI("Engine created, initializing Tune...");
    Tune::init(engine->get_options());
    LOGI("Tune initialized");

    // Set network verification callback for diagnostics
    engine->set_on_verify_networks([](std::string_view msg) {
        LOGI("NNUE Verify: %.*s", (int)msg.size(), msg.data());
    });

    // Verify NNUE loaded correctly
    LOGI("Verifying NNUE network...");
    engine->verify_networks();
    LOGI("NNUE network verified OK");

    engine->set_on_bestmove([](std::string_view bestmove, std::string_view ponder) {
        std::stringstream ss;
        ss << "bestmove " << bestmove;
        if (!ponder.empty()) {
            ss << " ponder " << ponder;
        }
        output_callback(ss.str());
    });

    engine->set_on_update_no_moves([](const Engine::InfoShort& info) {
        std::stringstream ss;
        ss << "info depth " << info.depth
           << " score " << UCIEngine::format_score(info.score);
        output_callback(ss.str());
    });

    engine->set_on_update_full([](const Engine::InfoFull& info) {
        std::stringstream ss;
        ss << "info"
           << " depth " << info.depth
           << " seldepth " << info.selDepth
           << " multipv " << info.multiPV
           << " score " << UCIEngine::format_score(info.score);

        if (!info.bound.empty())
            ss << " " << info.bound;

        if (!info.wdl.empty())
            ss << " wdl " << info.wdl;

        ss << " nodes " << info.nodes
           << " nps " << info.nps
           << " hashfull " << info.hashfull
           << " time " << info.timeMs;

        if (!info.pv.empty())
            ss << " pv " << info.pv;

        output_callback(ss.str());
    });

    engine->set_on_iter([](const Engine::InfoIter& info) {
        std::stringstream ss;
        ss << "info depth " << info.depth
           << " currmove " << info.currmove
           << " currmovenumber " << info.currmovenumber;
        output_callback(ss.str());
    });

    // 阶段3：加锁，原子性地设置全局状态
    lock.lock();
    g_engine = std::move(engine);
    g_engine_initialized = true;
    g_engine_running = true;
    g_nnue_loaded = true;
    g_initializing = false;
    g_cv.notify_all();
    lock.unlock();

    // output_callback 需要获取 g_mutex，必须在锁外调用
    output_callback("uciok");
    LOGI("Engine initialization complete");

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_AICore_PikafishAI_nativeSendCommand(JNIEnv* env, jobject thiz, jstring command) {
    const char* cmd = env->GetStringUTFChars(command, nullptr);
    std::string command_str(cmd);
    env->ReleaseStringUTFChars(command, cmd);

    std::vector<std::string> tokens = split_string(command_str, ' ');

    LOGI("nativeSendCommand: %s", command_str.c_str());

    if (command_str == "quit") {
        std::unique_lock<std::mutex> lock(g_mutex);
        // 等待初始化完成，避免打断正在进行的 nativeInit
        while (g_initializing) {
            g_cv.wait(lock);
        }
        Engine* engine = g_engine.get();
        if (engine) {
            g_is_searching = false;
            // 先设置 g_engine_running = false，让 nativeReadLine 能退出（解除可能的阻塞）
            g_engine_running = false;
            g_cv.notify_all();
            // 释放锁后再 stop + wait，避免死锁
            // （搜索结束时 output_callback 会尝试获取 g_mutex）
            lock.unlock();
            engine->stop();
            engine->wait_for_search_finished();
            lock.lock();
            g_engine.reset();
            g_engine_initialized = false;
            g_nnue_loaded = false;
            // 清空输出队列，避免残留数据干扰下次初始化
            while (!g_output_queue.empty()) g_output_queue.pop();
            g_cv.notify_all();
        }
        return;
    }

    if (command_str == "isready") {
        output_callback("readyok");
        return;
    }

    if (command_str == "uci") {
        output_callback("id name Pikafish 2026-01-02");
        output_callback("id author Pikafish developers");
        output_callback("uciok");
        return;
    }

    // ---- All engine-modifying commands are serialized with g_mutex ----
    std::unique_lock<std::mutex> lock(g_mutex);

    if (!g_engine || !g_engine_initialized) {
        LOGW("Engine not initialized, ignoring command: %s", command_str.c_str());
        return;
    }

    if (tokens.empty()) {
        return;
    }

    std::string cmd_name = tokens[0];

    if (cmd_name == "setoption") {
        if (tokens.size() >= 4 && tokens[1] == "name") {
            size_t name_start = 2;
            size_t value_start = std::string::npos;

            for (size_t i = 2; i < tokens.size(); ++i) {
                if (tokens[i] == "value") {
                    value_start = i + 1;
                    break;
                }
            }

            if (value_start != std::string::npos && value_start < tokens.size()) {
                std::string name;
                for (size_t i = name_start; i < value_start - 1; ++i) {
                    if (i > name_start) name += " ";
                    name += tokens[i];
                }

                // Skip unsupported options silently
                if (UNSUPPORTED_OPTIONS.count(name)) {
                    LOGI("Skipping unsupported option: %s", name.c_str());
                    return;
                }

                std::string value;
                for (size_t i = value_start; i < tokens.size(); ++i) {
                    if (i > value_start) value += " ";
                    value += tokens[i];
                }

                LOGI("setoption: name='%s' value='%s'", name.c_str(), value.c_str());
                std::istringstream iss("name " + name + " value " + value);
                g_engine->get_options().setoption(iss);
            }
        }
    } else if (cmd_name == "position") {
        // Ensure any running search is fully stopped before changing position
        if (g_is_searching) {
            LOGI("position: stopping active search first");
            g_engine->stop();
            // Release mutex while waiting for search to finish (avoids deadlock)
            lock.unlock();
            g_engine->wait_for_search_finished();
            lock.lock();
            g_is_searching = false;
        }

        std::string fen;
        std::vector<std::string> moves;

        size_t i = 1;
        if (i < tokens.size() && tokens[i] == "fen") {
            ++i;
            while (i < tokens.size() && tokens[i] != "moves") {
                if (!fen.empty()) fen += " ";
                fen += tokens[i];
                ++i;
            }

            if (i < tokens.size() && tokens[i] == "moves") {
                ++i;
                while (i < tokens.size()) {
                    moves.push_back(tokens[i]);
                    ++i;
                }
            }
        } else if (i < tokens.size() && tokens[i] == "startpos") {
            fen = StartFEN;
            ++i;
            if (i < tokens.size() && tokens[i] == "moves") {
                ++i;
                while (i < tokens.size()) {
                    moves.push_back(tokens[i]);
                    ++i;
                }
            }
        }

        if (!fen.empty()) {
            LOGI("Setting position: fen=%s moves=%zu", fen.c_str(), moves.size());
            g_engine->set_position(fen, moves);
            LOGI("Position set OK");
        } else {
            LOGW("position command with empty fen");
        }
    } else if (cmd_name == "go") {
        // Ensure no search is already running
        if (g_is_searching) {
            LOGW("go: engine already searching, stopping first");
            g_engine->stop();
            lock.unlock();
            g_engine->wait_for_search_finished();
            lock.lock();
            g_is_searching = false;
        }

        Search::LimitsType limits;
        // time/inc 保持默认值 0，禁用时间管理（use_time_management() 返回 false）
        // 只通过 movetime 和 depth 限制搜索
        limits.depth = 0;   // 0 表示不限制深度（由 go 命令的 depth 参数覆盖）
        limits.nodes = 0;   // 0 表示不限制节点数

        size_t i = 1;
        while (i < tokens.size()) {
            if (tokens[i] == "depth" && i + 1 < tokens.size()) {
                int val;
                if (safe_stoi(tokens[i + 1], val)) {
                    limits.depth = val;
                } else {
                    LOGE("Invalid depth value: %s", tokens[i + 1].c_str());
                }
                i += 2;
            } else if (tokens[i] == "nodes" && i + 1 < tokens.size()) {
                int64_t val;
                if (safe_stoll(tokens[i + 1], val)) {
                    limits.nodes = val;
                } else {
                    LOGE("Invalid nodes value: %s", tokens[i + 1].c_str());
                }
                i += 2;
            } else if (tokens[i] == "movetime" && i + 1 < tokens.size()) {
                int val;
                if (safe_stoi(tokens[i + 1], val)) {
                    limits.movetime = val;
                } else {
                    LOGE("Invalid movetime value: %s", tokens[i + 1].c_str());
                }
                i += 2;
            } else {
                ++i;
            }
        }

        LOGI("go: depth=%d movetime=%lld nodes=%lld", limits.depth, (long long)limits.movetime, (long long)limits.nodes);

        // 设置搜索开始时间（UCI 标准做法，用于时间管理计算 elapsed）
        limits.startTime = now();

        if (!g_nnue_loaded) {
            LOGE("NNUE not loaded, cannot start search!");
            output_callback("info string ERROR: NNUE network not loaded, cannot search");
            return;
        }

        g_is_searching = true;
        // Release lock before go() to avoid deadlock with output_callback
        lock.unlock();
        g_engine->go(limits);
        LOGI("go: engine->go() returned (search started in background)");
    } else if (cmd_name == "stop") {
        LOGI("stop: stopping search");
        g_engine->stop();
        // Release mutex while waiting to avoid deadlock
        lock.unlock();
        g_engine->wait_for_search_finished();
        lock.lock();
        g_is_searching = false;
        LOGI("stop: search fully stopped");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_AICore_PikafishAI_nativeReadLine(JNIEnv* env, jobject thiz) {
    std::unique_lock<std::mutex> lock(g_mutex);

    g_cv.wait(lock, [] { return !g_output_queue.empty() || !g_engine_running; });

    if (g_output_queue.empty()) {
        return nullptr;
    }

    std::string line = g_output_queue.front();
    g_output_queue.pop();

    return env->NewStringUTF(line.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_AICore_PikafishAI_nativeCleanup(JNIEnv* env, jobject thiz) {
    LOGI("nativeCleanup called");
    std::unique_lock<std::mutex> lock(g_mutex);
    // 等待初始化完成，避免打断正在进行的 nativeInit
    while (g_initializing) {
        g_cv.wait(lock);
    }
    if (g_engine) {
        Engine* engine = g_engine.get();
        g_is_searching = false;
        // 先设置 g_engine_running = false，让 nativeReadLine 能退出
        g_engine_running = false;
        g_cv.notify_all();
        // 释放锁后再 stop + wait，避免死锁
        // （搜索结束时 output_callback 会尝试获取 g_mutex）
        lock.unlock();
        engine->stop();
        engine->wait_for_search_finished();
        lock.lock();
        g_engine.reset();
        g_engine_initialized = false;
        g_nnue_loaded = false;
        // 清空输出队列，避免重试时残留消息干扰
        while (!g_output_queue.empty()) g_output_queue.pop();
        g_cv.notify_all();
    }
    LOGI("nativeCleanup complete");
}

// 查询当前局面的长将/长捉/和棋判定（基于引擎内置的 rule_judge 权威规则）。
// 自行用临时 Position 重建完整走子历史，不触碰 g_engine，与搜索线程无竞争，也无需加载 NNUE。
// 返回 int[4]: [ruleType, forbiddenSide, isCheck, 0]
//   ruleType: 0=无 1=一方违规(长将/长捉) 2=和棋
//   forbiddenSide: 判负(犯规)方 0=红(WHITE) 1=黑(BLACK)；和棋时为 255
//   isCheck: 是否涉及长将 1=是 0=否(否则视为长捉)
extern "C" JNIEXPORT jintArray JNICALL
Java_AICore_PikafishAI_nativeRuleJudge(JNIEnv* env, jobject thiz, jstring fen, jobjectArray moves) {
    jint buf[4] = {0, 255, 0, 0};

    if (fen != nullptr) {
        const char* f = env->GetStringUTFChars(fen, nullptr);
        std::string fenStr(f);
        env->ReleaseStringUTFChars(fen, f);

        // 收集完整 UCI 走子序列（rule_judge 需要回溯历史）
        std::vector<std::string> moveList;
        if (moves != nullptr) {
            jsize n = env->GetArrayLength(moves);
            for (jsize i = 0; i < n; ++i) {
                jstring s = (jstring) env->GetObjectArrayElement(moves, i);
                if (s != nullptr) {
                    const char* m = env->GetStringUTFChars(s, nullptr);
                    moveList.push_back(std::string(m));
                    env->ReleaseStringUTFChars(s, m);
                    env->DeleteLocalRef(s);
                }
            }
        }

        // 临时 Position 重建完整历史：do_move 会自动维护 filter（重复检测）
        Position pos;
        StateListPtr states(new std::deque<StateInfo>(1));
        pos.set(fenStr, &states->back());

        for (const auto& mv : moveList) {
            Move m = UCIEngine::to_move(pos, mv);
            if (m == Move::none())
                break;
            states->emplace_back();
            pos.do_move(m, states->back());
        }

        // 引擎未提供 analyze_rule, 改用已有的 rule_judge 并在 JNI 层映射成
        // (ruleType, forbiddenSide, isCheck):
        //   ruleType     : 0=无 1=一方违规(长将/长捉) 2=和棋
        //   forbiddenSide: 判负方 0=红(WHITE) 1=黑(BLACK); 和棋时 255
        //   isCheck      : 是否长将 1=是 0=否(长捉, 此处保守标记长将)
        int ruleType = 0, forbiddenSide = 255, isCheck = 0;
        Value ruleResult = VALUE_NONE;
        bool judged = pos.rule_judge(ruleResult, 0);
        if (judged)
        {
            if (ruleResult == VALUE_DRAW)
            {
                ruleType = 2;
                forbiddenSide = 255;
                isCheck = 0;
            }
            else
            {
                ruleType = 1;
                int us = int(pos.side_to_move());
                forbiddenSide = (ruleResult > 0) ? (1 - us) : us;
                isCheck = 1;  // rule_judge 的 Value 不区分长将/长捉, 保守标长将
            }
        }
        buf[0] = ruleType;
        buf[1] = forbiddenSide;
        buf[2] = isCheck;
    }

    jintArray arr = env->NewIntArray(4);
    env->SetIntArrayRegion(arr, 0, 4, buf);
    return arr;
}
