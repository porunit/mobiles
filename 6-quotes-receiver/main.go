// Command quotes-receiver reads quote ticks from the driver device and
// publishes them to RabbitMQ (quotes.topic). It is the single source of quotes
// for the platform — a producer only (no consuming, no client-facing API).
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/prometheus/client_golang/prometheus/promhttp"
	amqp "github.com/rabbitmq/amqp091-go"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"

	"github.com/rmp-trading/quotes-receiver/internal/driver"
	"github.com/rmp-trading/quotes-receiver/internal/metrics"
	"github.com/rmp-trading/quotes-receiver/internal/publisher"
	"github.com/rmp-trading/quotes-receiver/internal/quotes"
	"github.com/rmp-trading/quotes-receiver/internal/telemetry"
)

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))

	cfg := struct {
		devicePath   string
		rabbitURL    string
		exchange     string
		routingPfx   string
		confirms     bool
		otelEndpoint string
		serviceName  string
		httpAddr     string
	}{
		devicePath:   env("DEVICE_PATH", "/dev-shared/financial_quotes"),
		rabbitURL:    env("RABBITMQ_URL", "amqp://app:app_dev_pass@rabbitmq:5672/"),
		exchange:     env("QUOTES_EXCHANGE", "quotes.topic"),
		routingPfx:   env("QUOTES_ROUTING_PREFIX", "quote.tick"),
		confirms:     env("PUBLISHER_CONFIRMS", "true") == "true",
		otelEndpoint: os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"),
		serviceName:  env("OTEL_SERVICE_NAME", "go-receiver"),
		httpAddr:     env("HTTP_ADDR", ":8090"),
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// --- tracing (best-effort; degrade to no-op if unset/unreachable) ---
	if cfg.otelEndpoint != "" {
		shutdown, err := telemetry.Init(ctx, cfg.serviceName, cfg.otelEndpoint)
		if err != nil {
			log.Warn("otel init failed; continuing without tracing", "err", err)
		} else {
			log.Info("otel tracing enabled", "endpoint", cfg.otelEndpoint)
			defer func() {
				sctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
				defer cancel()
				_ = shutdown(sctx)
			}()
		}
	}
	tracer := otel.Tracer("quotes-receiver")
	propagator := otel.GetTextMapPropagator()

	// --- RabbitMQ publisher ---
	pub := publisher.New(cfg.rabbitURL, cfg.exchange, cfg.confirms, log)
	if err := pub.Connect(ctx); err != nil {
		log.Error("could not connect to rabbitmq", "err", err)
		os.Exit(1)
	}
	defer pub.Close()
	var ready atomic.Bool
	ready.Store(true)

	// --- HTTP: health + metrics ---
	mux := http.NewServeMux()
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, _ *http.Request) {
		if ready.Load() {
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte("ok"))
			return
		}
		w.WriteHeader(http.StatusServiceUnavailable)
	})
	mux.Handle("/metrics", promhttp.Handler())
	srv := &http.Server{Addr: cfg.httpAddr, Handler: mux}
	go func() {
		log.Info("http listening", "addr", cfg.httpAddr)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Error("http server error", "err", err)
			stop()
		}
	}()

	// --- read → parse → publish loop ---
	rd := driver.New(cfg.devicePath, log)
	handle := func(line []byte) {
		metrics.QuotesRead.Inc()

		q, err := quotes.Parse(line)
		if err != nil {
			if !errors.Is(err, quotes.ErrEmpty) {
				metrics.ParseErrors.Inc()
				log.Debug("parse error", "err", err, "line", string(line))
			}
			return
		}

		spanCtx, span := tracer.Start(ctx, "publish_quote")
		span.SetAttributes(
			attribute.String("quote.symbol", q.Symbol),
			attribute.String("messaging.destination", cfg.exchange),
		)

		headers := amqp.Table{}
		propagator.Inject(spanCtx, amqpCarrier(headers))
		rk := cfg.routingPfx + "." + q.Symbol

		t0 := time.Now()
		if perr := pub.Publish(spanCtx, rk, line, headers); perr != nil {
			metrics.PublishErrors.Inc()
			span.RecordError(perr)
			span.SetStatus(codes.Error, "publish failed")
			log.Warn("publish failed", "symbol", q.Symbol, "err", perr)
		} else {
			metrics.PublishLatency.Observe(time.Since(t0).Seconds())
			metrics.QuotesPublished.WithLabelValues(q.Symbol).Inc()
		}
		span.End()
	}

	runErr := rd.Run(ctx, handle)
	if runErr != nil && !errors.Is(runErr, context.Canceled) {
		log.Error("reader stopped", "err", runErr)
	}

	// --- graceful shutdown ---
	log.Info("shutting down")
	ready.Store(false)
	shctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = srv.Shutdown(shctx)
}
