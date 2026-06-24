/*
 * quote_gen — user-space exchange emulator (iteration 1).
 *
 * Generates random-walk financial quotes in fixed-point (micros, x1e6) and
 * streams them as newline-delimited JSON (NDJSON, one QuoteTick per line) into
 * a FIFO in a shared volume. The Go receiver (6-quotes-receiver) reads the FIFO
 * and publishes each tick to RabbitMQ.
 *
 * This is the cross-platform stand-in for the Linux kernel character-device
 * driver (the real .ko is step 2): same NDJSON wire contract, so the receiver
 * is unchanged when the kernel module replaces this binary.
 *
 * Wire contract — one JSON object per line:
 *   {"symbol":"SBER","bid":285.500000,"ask":285.620000,"spread":0.120000,
 *    "timestamp":1750000000000,"volume":200000,"change_percent":0.34}
 *
 * Env:
 *   DEVICE_PATH     path of the FIFO to create/stream (default /shared-dev/financial_quotes)
 *   QUOTE_RATE_HZ   ticks per second PER instrument (default 200)
 *   INSTRUMENT_SET  comma-separated symbols (default SBER,GAZP,YNDX,LKOH,VTBR,AAPL,TSLA)
 *   LOG_LEVEL       debug|info|warn|error (default info) — logs go to stderr
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>
#include <stdint.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/types.h>

#define MAX_INSTRUMENTS 64
#define SYMBOL_MAXLEN   16
#define LINE_BUFSZ      256

/* ------- logging ------------------------------------------------------- */
static int g_loglevel = 2; /* 0 debug,1.. ; default info=1? use: error<warn<info<debug */
enum { LVL_ERROR = 0, LVL_WARN = 1, LVL_INFO = 2, LVL_DEBUG = 3 };

static void log_msg(int lvl, const char *fmt, ...) {
    if (lvl > g_loglevel) return;
    static const char *names[] = {"ERROR", "WARN", "INFO", "DEBUG"};
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    fprintf(stderr, "%ld.%03ld [%s] ", (long)ts.tv_sec, ts.tv_nsec / 1000000L, names[lvl]);
    va_list ap; va_start(ap, fmt); vfprintf(stderr, fmt, ap); va_end(ap);
    fputc('\n', stderr);
}

/* ------- instrument state --------------------------------------------- */
typedef struct {
    char    symbol[SYMBOL_MAXLEN];
    int64_t mid_micros;      /* current mid price, fixed-point x1e6 */
    int64_t prev_mid_micros; /* previous mid (for change_percent)   */
} instrument_t;

static instrument_t g_inst[MAX_INSTRUMENTS];
static int          g_inst_count = 0;

/* deterministic-ish PRNG (xorshift64) so we don't depend on libc rand quality */
static uint64_t g_rng;
static inline uint64_t xorshift64(void) {
    uint64_t x = g_rng;
    x ^= x << 13; x ^= x >> 7; x ^= x << 17;
    return g_rng = x;
}
/* signed step in [-range, +range] micros */
static inline int64_t rand_step(int64_t range) {
    if (range <= 0) return 0;
    return (int64_t)(xorshift64() % (uint64_t)(2 * range + 1)) - range;
}

/* seed each instrument with a plausible starting price (micros) */
static int64_t seed_price(const char *sym) {
    /* spread starting prices apart so the dashboard looks alive */
    uint64_t h = 1469598103934665603ULL;
    for (const char *p = sym; *p; p++) { h ^= (uint8_t)*p; h *= 1099511628211ULL; }
    int64_t base = 50000000 + (int64_t)(h % 450000000ULL); /* 50.0 .. 500.0 */
    return base;
}

static volatile sig_atomic_t g_stop = 0;
static void on_signal(int s) { (void)s; g_stop = 1; }

/* ------- helpers ------------------------------------------------------- */
static int parse_instruments(const char *csv) {
    char buf[1024];
    snprintf(buf, sizeof buf, "%s", csv);
    char *save = NULL;
    for (char *tok = strtok_r(buf, ",", &save);
         tok && g_inst_count < MAX_INSTRUMENTS;
         tok = strtok_r(NULL, ",", &save)) {
        /* trim spaces */
        while (*tok == ' ') tok++;
        size_t n = strlen(tok);
        while (n > 0 && (tok[n - 1] == ' ' || tok[n - 1] == '\n' || tok[n - 1] == '\r')) tok[--n] = 0;
        if (n == 0) continue;
        instrument_t *it = &g_inst[g_inst_count++];
        snprintf(it->symbol, sizeof it->symbol, "%s", tok);
        it->mid_micros = seed_price(it->symbol);
        it->prev_mid_micros = it->mid_micros;
    }
    return g_inst_count;
}

/* format micros as a fixed 6-decimal string into out (integer math, no float drift) */
static void fmt_micros(int64_t v, char *out, size_t outsz) {
    int neg = v < 0; if (neg) v = -v;
    snprintf(out, outsz, "%s%lld.%06lld", neg ? "-" : "",
             (long long)(v / 1000000), (long long)(v % 1000000));
}

/* build one NDJSON QuoteTick line; returns length written */
static int build_line(instrument_t *it, int64_t ts_ms, char *out, size_t outsz) {
    /* spread ~0.04% of mid, floored at 0.01 */
    int64_t spread = it->mid_micros / 2500;
    if (spread < 10000) spread = 10000;
    int64_t bid = it->mid_micros - spread / 2;
    int64_t ask = it->mid_micros + spread / 2;

    /* change_percent vs previous mid, guard divide-by-zero */
    double change_pct = 0.0;
    if (it->prev_mid_micros != 0)
        change_pct = ((double)(it->mid_micros - it->prev_mid_micros) /
                      (double)it->prev_mid_micros) * 100.0;

    uint64_t volume = 50000 + (xorshift64() % 250000);

    char sbid[32], sask[32], sspread[32];
    fmt_micros(bid, sbid, sizeof sbid);
    fmt_micros(ask, sask, sizeof sask);
    fmt_micros(spread, sspread, sizeof sspread);

    return snprintf(out, outsz,
        "{\"symbol\":\"%s\",\"bid\":%s,\"ask\":%s,\"spread\":%s,"
        "\"timestamp\":%lld,\"volume\":%llu,\"change_percent\":%.2f}\n",
        it->symbol, sbid, sask, sspread,
        (long long)ts_ms, (unsigned long long)volume, change_pct);
}

static int64_t now_ms(void) {
    struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000L;
}

/* write all bytes, handling short writes; returns 0 ok, -1 on error (EPIPE etc.) */
static int write_all(int fd, const char *buf, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t w = write(fd, buf + off, len - off);
        if (w < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        off += (size_t)w;
    }
    return 0;
}

int main(void) {
    /* --- config from env --- */
    const char *path = getenv("DEVICE_PATH");
    if (!path || !*path) path = "/shared-dev/financial_quotes";
    const char *hz_s = getenv("QUOTE_RATE_HZ");
    long hz = hz_s && *hz_s ? strtol(hz_s, NULL, 10) : 200;
    if (hz < 1) hz = 1; if (hz > 100000) hz = 100000;
    const char *iset = getenv("INSTRUMENT_SET");
    if (!iset || !*iset) iset = "SBER,GAZP,YNDX,LKOH,VTBR,AAPL,TSLA";
    const char *lvl = getenv("LOG_LEVEL");
    if (lvl) {
        if (!strcmp(lvl, "debug")) g_loglevel = LVL_DEBUG;
        else if (!strcmp(lvl, "warn")) g_loglevel = LVL_WARN;
        else if (!strcmp(lvl, "error")) g_loglevel = LVL_ERROR;
        else g_loglevel = LVL_INFO;
    }

    /* seed PRNG from clock + pid (non-zero) */
    g_rng = (uint64_t)now_ms() ^ ((uint64_t)getpid() << 32);
    if (g_rng == 0) g_rng = 0x9e3779b97f4a7c15ULL;

    if (parse_instruments(iset) == 0) {
        log_msg(LVL_ERROR, "no instruments parsed from INSTRUMENT_SET='%s'", iset);
        return 1;
    }

    /* SIGPIPE must not kill us: reader disconnects are expected (restart). */
    signal(SIGPIPE, SIG_IGN);
    signal(SIGINT, on_signal);
    signal(SIGTERM, on_signal);

    /* create the FIFO (the "device" node) up front so the healthcheck passes
       and the receiver can open it. EEXIST is fine across restarts. */
    if (mkfifo(path, 0666) < 0 && errno != EEXIST) {
        log_msg(LVL_ERROR, "mkfifo(%s) failed: %s", path, strerror(errno));
        return 1;
    }
    log_msg(LVL_INFO, "fifo ready at %s | %d instruments | %ld Hz/instrument",
            path, g_inst_count, hz);

    const long period_ns = 1000000000L / hz;
    char line[LINE_BUFSZ];

    while (!g_stop) {
        /* blocks until a reader (go-receiver) opens the FIFO */
        int fd = open(path, O_WRONLY);
        if (fd < 0) {
            if (errno == EINTR) continue;
            log_msg(LVL_ERROR, "open(%s) failed: %s", path, strerror(errno));
            sleep(1);
            continue;
        }
        log_msg(LVL_INFO, "reader connected; streaming");

        struct timespec next;
        clock_gettime(CLOCK_MONOTONIC, &next);
        uint64_t emitted = 0;
        int broken = 0;

        while (!g_stop && !broken) {
            int64_t ts = now_ms();
            for (int i = 0; i < g_inst_count; i++) {
                instrument_t *it = &g_inst[i];
                /* random walk: ~0.05% of price per step, floored */
                int64_t range = it->mid_micros / 2000;
                if (range < 5000) range = 5000;
                it->prev_mid_micros = it->mid_micros;
                it->mid_micros += rand_step(range);
                if (it->mid_micros < 1000000) it->mid_micros = 1000000; /* floor 1.0 */

                int n = build_line(it, ts, line, sizeof line);
                if (n <= 0 || n >= (int)sizeof line) continue;
                if (write_all(fd, line, (size_t)n) < 0) {
                    log_msg(LVL_WARN, "write failed (%s); reader gone, reopening",
                            strerror(errno));
                    broken = 1;
                    break;
                }
                emitted++;
            }
            if (g_loglevel >= LVL_DEBUG && (emitted % (uint64_t)(hz * g_inst_count) == 0))
                log_msg(LVL_DEBUG, "emitted=%llu", (unsigned long long)emitted);

            /* steady pacing on an absolute clock to avoid drift */
            next.tv_nsec += period_ns;
            while (next.tv_nsec >= 1000000000L) { next.tv_nsec -= 1000000000L; next.tv_sec++; }
            clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &next, NULL);
        }
        close(fd);
    }

    log_msg(LVL_INFO, "shutting down; removing %s", path);
    unlink(path);
    return 0;
}
