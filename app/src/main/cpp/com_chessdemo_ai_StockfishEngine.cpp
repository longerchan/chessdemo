#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <condition_variable>
#include <atomic>
#include <sstream>

#include <android/log.h>

#include "engine.h"
#include "search.h"
#include "score.h"
#include "bitboard.h"
#include "ucioption.h"
#include "misc.h"

using namespace Stockfish;
using namespace Stockfish::Search;

// Atomic pointer so nativeStop can read without locking g_engine_mutex.
static std::atomic<Engine*> g_engine{nullptr};
static std::mutex g_engine_mutex; // Still protects nativeInit/nativeSetPosition/nativeSetOption

static std::string g_bestmove;
static std::mutex g_bestmove_mutex;
static std::condition_variable g_bestmove_cv;

static std::string g_latest_info;
static std::mutex g_info_mutex;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeInit(JNIEnv* env, jobject thiz, jint ttSizeMB, jint threadCount) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);

    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "Calling Bitboards::init()...");
    Bitboards::init();
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "Bitboards::init() done");

    Engine* eng = new Engine();
    eng->set_tt_size(static_cast<size_t>(ttSizeMB));

    // Set thread count BEFORE search_clear to avoid race with thread initialization
    {
        std::string cmd = "name Threads value " + std::to_string(threadCount);
        std::istringstream ss(cmd);
        eng->get_options().setoption(ss);
        __android_log_print(ANDROID_LOG_INFO, "Stockfish", "Threads set to %d", threadCount);
    }

    eng->search_clear();

    eng->set_on_bestmove([](std::string_view bestmove, std::string_view /*ponder*/) {
        __android_log_print(ANDROID_LOG_INFO, "Stockfish", "bestmove callback: %.*s",
                           (int)bestmove.size(), bestmove.data());
        std::lock_guard<std::mutex> lock(g_bestmove_mutex);
        g_bestmove = std::string(bestmove);
        g_bestmove_cv.notify_one();
    });

    eng->set_on_update_full([](const InfoFull& info) {
        std::lock_guard<std::mutex> lock(g_info_mutex);
        std::string scoreStr;
        if (info.score.is<Score::Mate>()) {
            scoreStr = "mate " + std::to_string(info.score.get<Score::Mate>().plies);
        } else {
            scoreStr = "cp " + std::to_string(info.score.get<Score::InternalUnits>().value);
        }
        g_latest_info = "depth " + std::to_string(info.depth)
                      + " seldepth " + std::to_string(info.selDepth)
                      + " " + scoreStr
                      + " nodes " + std::to_string(info.nodes)
                      + " nps " + std::to_string(info.nps)
                      + " time " + std::to_string(info.timeMs)
                      + " pv " + std::string(info.pv);
    });

    eng->set_on_verify_network([](std::string_view msg) {
        __android_log_print(ANDROID_LOG_INFO, "Stockfish", "verify_network: %.*s",
                           (int)msg.size(), msg.data());
    });

    g_engine.store(eng, std::memory_order_release);
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "Engine initialized successfully");
    return reinterpret_cast<jlong>(eng);
}

JNIEXPORT jstring JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeSetPosition(JNIEnv* env, jobject thiz,
                                                        jstring fen, jobjectArray moves) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (!eng) return env->NewStringUTF("error");

    const char* fenStr = env->GetStringUTFChars(fen, nullptr);
    std::string fenCpp(fenStr);
    env->ReleaseStringUTFChars(fen, fenStr);

    jsize moveCount = env->GetArrayLength(moves);
    std::vector<std::string> moveList;
    moveList.reserve(moveCount);
    for (jsize i = 0; i < moveCount; ++i) {
        jstring jmove = (jstring)env->GetObjectArrayElement(moves, i);
        const char* moveStr = env->GetStringUTFChars(jmove, nullptr);
        moveList.emplace_back(moveStr);
        env->ReleaseStringUTFChars(jmove, moveStr);
    }

    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "set_position: fen=%s moves=%zu", fenCpp.c_str(), moveList.size());

    eng->wait_for_search_finished();
    eng->search_clear();

    auto result = eng->set_position(fenCpp, moveList);
    if (result.has_value()) {
        __android_log_print(ANDROID_LOG_WARN, "Stockfish", "set_position error: %s", result->what());
        return env->NewStringUTF("error");
    }
    return env->NewStringUTF("ok");
}

JNIEXPORT jstring JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeGo(JNIEnv* env, jobject thiz,
                                               jint depth,
                                               jint wTime, jint bTime,
                                               jint wInc, jint bInc,
                                               jint movestogo, jint movetime,
                                               jint nodes, jint mate,
                                               jboolean infinite) {
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeGo: depth=%d wTime=%d bTime=%d wInc=%d bInc=%d movestogo=%d movetime=%d nodes=%d mate=%d infinite=%d",
        depth, wTime, bTime, wInc, bInc, movestogo, movetime, nodes, mate, infinite ? 1 : 0);

    // Clear bestmove and info before starting
    {
        std::lock_guard<std::mutex> bm_lock(g_bestmove_mutex);
        g_bestmove.clear();
    }
    {
        std::lock_guard<std::mutex> lock(g_info_mutex);
        g_latest_info.clear();
    }

    Search::LimitsType limits;
    limits.depth = depth;
    limits.time[WHITE] = static_cast<Stockfish::TimePoint>(wTime);
    limits.time[BLACK] = static_cast<Stockfish::TimePoint>(bTime);
    limits.inc[WHITE] = static_cast<Stockfish::TimePoint>(wInc);
    limits.inc[BLACK] = static_cast<Stockfish::TimePoint>(bInc);
    limits.movestogo = movestogo;
    limits.movetime = static_cast<Stockfish::TimePoint>(movetime);
    limits.nodes = static_cast<uint64_t>(nodes);
    limits.mate = mate;
    limits.infinite = infinite ? 1 : 0;
    limits.startTime = Stockfish::now();

    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (!eng) return env->NewStringUTF("none");

    // Wait for any previous search to finish, then start new search.
    // Hold g_engine_mutex only for the Engine API calls, then release it
    // before waiting on g_bestmove_cv.
    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        // Re-check engine pointer after acquiring mutex
        eng = g_engine.load(std::memory_order_acquire);
        if (!eng) return env->NewStringUTF("none");
        eng->wait_for_search_finished();
        eng->go(limits);
    }
    // g_engine_mutex is released here.

    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeGo: go() returned, waiting for bestmove...");

    // Wait for bestmove. Predicate also checks for sentinel "none" set by nativeStop.
    int waitSeconds = (wTime > 0 || bTime > 0 || movetime > 0) ? 120 : 30;
    std::unique_lock<std::mutex> bm_lock(g_bestmove_mutex);
    bool signaled = g_bestmove_cv.wait_for(bm_lock, std::chrono::seconds(waitSeconds),
        [] { return !g_bestmove.empty(); });
    std::string result = g_bestmove;
    bm_lock.unlock();

    if (!signaled) {
        __android_log_print(ANDROID_LOG_WARN, "Stockfish", "nativeGo: TIMEOUT waiting for bestmove");
        return env->NewStringUTF("none");
    }

    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeGo: bestmove received: %s", result.c_str());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeSetOption(JNIEnv* env, jobject thiz,
                                                      jstring name, jstring value) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (!eng) return;

    const char* nameStr = env->GetStringUTFChars(name, nullptr);
    const char* valueStr = env->GetStringUTFChars(value, nullptr);

    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "set_option: %s=%s", nameStr, valueStr);

    std::string cmd = "name " + std::string(nameStr) + " value " + std::string(valueStr);
    std::istringstream ss(cmd);
    eng->get_options().setoption(ss);

    env->ReleaseStringUTFChars(name, nameStr);
    env->ReleaseStringUTFChars(value, valueStr);
}

JNIEXPORT void JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeStop(JNIEnv* env, jobject thiz) {
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeStop called");
    // Read atomic pointer WITHOUT holding g_engine_mutex — this is the critical fix.
    // Previously this acquired g_engine_mutex, which could be held by nativeGo's go() call,
    // blocking the UI thread entirely.
    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (eng) {
        eng->stop();
    }
    // Set sentinel value so nativeGo's wait predicate returns true immediately.
    {
        std::lock_guard<std::mutex> bm_lock(g_bestmove_mutex);
        g_bestmove = "none";
        g_bestmove_cv.notify_one();
    }
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeStop done");
}

JNIEXPORT jstring JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeGetLatestInfo(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_info_mutex);
    return env->NewStringUTF(g_latest_info.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeGoInfinite(JNIEnv* env, jobject thiz,
                                                       jint depth, jint multiPV) {
    __android_log_print(ANDROID_LOG_INFO, "Stockfish", "nativeGoInfinite: depth=%d multiPV=%d",
        depth, multiPV);

    {
        std::lock_guard<std::mutex> bm_lock(g_bestmove_mutex);
        g_bestmove.clear();
    }
    {
        std::lock_guard<std::mutex> lock(g_info_mutex);
        g_latest_info.clear();
    }

    Search::LimitsType limits;
    limits.infinite = 1;
    limits.depth = depth;
    limits.startTime = Stockfish::now();

    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (!eng) return env->NewStringUTF("error");

    {
        std::lock_guard<std::mutex> lock(g_engine_mutex);
        eng = g_engine.load(std::memory_order_acquire);
        if (!eng) return env->NewStringUTF("error");
        eng->go(limits);
    }
    return env->NewStringUTF("ok");
}

JNIEXPORT void JNICALL
Java_com_chessdemo_ai_StockfishEngine_nativeQuit(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    Engine* eng = g_engine.load(std::memory_order_acquire);
    if (eng) {
        eng->stop();
        eng->wait_for_search_finished();
        delete eng;
        g_engine.store(nullptr, std::memory_order_release);
    }
}

} // extern "C"
