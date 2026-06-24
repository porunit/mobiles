// Package metrics holds the Prometheus collectors exposed at /metrics.
package metrics

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
)

var (
	QuotesRead = promauto.NewCounter(prometheus.CounterOpts{
		Name: "receiver_quotes_read_total",
		Help: "Lines read from the driver device.",
	})
	ParseErrors = promauto.NewCounter(prometheus.CounterOpts{
		Name: "receiver_parse_errors_total",
		Help: "Lines that failed to parse as a QuoteTick.",
	})
	QuotesPublished = promauto.NewCounterVec(prometheus.CounterOpts{
		Name: "receiver_quotes_published_total",
		Help: "Quotes successfully published (confirmed) to RabbitMQ, by symbol.",
	}, []string{"symbol"})
	PublishErrors = promauto.NewCounter(prometheus.CounterOpts{
		Name: "receiver_publish_errors_total",
		Help: "Publish attempts that failed or were nacked.",
	})
	PublishLatency = promauto.NewHistogram(prometheus.HistogramOpts{
		Name:    "receiver_publish_confirm_seconds",
		Help:    "Latency from publish to broker confirm.",
		Buckets: prometheus.DefBuckets,
	})
)
