// Package publisher publishes quote messages to a RabbitMQ topic exchange with
// publisher confirms and lazy reconnect.
package publisher

import (
	"context"
	"fmt"
	"log/slog"
	"sync"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

type Publisher struct {
	url      string
	exchange string
	confirms bool
	log      *slog.Logger

	mu   sync.Mutex
	conn *amqp.Connection
	ch   *amqp.Channel
}

func New(url, exchange string, confirms bool, log *slog.Logger) *Publisher {
	return &Publisher{url: url, exchange: exchange, confirms: confirms, log: log}
}

// Connect dials with retry until success or ctx is cancelled.
func (p *Publisher) Connect(ctx context.Context) error {
	for {
		p.mu.Lock()
		err := p.dial()
		p.mu.Unlock()
		if err == nil {
			p.log.Info("rabbitmq connected", "exchange", p.exchange, "confirms", p.confirms)
			return nil
		}
		p.log.Warn("rabbitmq connect failed; retrying", "err", err)
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(2 * time.Second):
		}
	}
}

// dial opens a fresh connection+channel. Caller holds p.mu.
func (p *Publisher) dial() error {
	conn, err := amqp.Dial(p.url)
	if err != nil {
		return err
	}
	ch, err := conn.Channel()
	if err != nil {
		conn.Close()
		return err
	}
	if p.confirms {
		if err := ch.Confirm(false); err != nil {
			ch.Close()
			conn.Close()
			return err
		}
	}
	p.conn, p.ch = conn, ch
	return nil
}

// reset tears down the current channel/connection. Caller holds p.mu.
func (p *Publisher) reset() {
	if p.ch != nil {
		p.ch.Close()
		p.ch = nil
	}
	if p.conn != nil {
		p.conn.Close()
		p.conn = nil
	}
}

// Publish sends body with routingKey and headers (trace context). With confirms
// enabled it blocks until the broker acks. On a channel error it reconnects and
// retries once.
func (p *Publisher) Publish(ctx context.Context, routingKey string, body []byte, headers amqp.Table) error {
	p.mu.Lock()
	defer p.mu.Unlock()

	if p.ch == nil {
		if err := p.dial(); err != nil {
			return err
		}
	}

	pub := amqp.Publishing{
		ContentType:  "application/json",
		DeliveryMode: amqp.Persistent,
		Timestamp:    time.Now(),
		Headers:      headers,
		Body:         body,
	}

	conf, err := p.publishOnce(ctx, routingKey, pub)
	if err != nil {
		p.log.Warn("publish failed; reconnecting and retrying once", "err", err)
		p.reset()
		if derr := p.dial(); derr != nil {
			return derr
		}
		conf, err = p.publishOnce(ctx, routingKey, pub)
		if err != nil {
			return err
		}
	}

	if p.confirms && conf != nil {
		ok, werr := conf.WaitContext(ctx)
		if werr != nil {
			return werr
		}
		if !ok {
			return fmt.Errorf("publish nacked by broker")
		}
	}
	return nil
}

func (p *Publisher) publishOnce(ctx context.Context, rk string, pub amqp.Publishing) (*amqp.DeferredConfirmation, error) {
	pctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	return p.ch.PublishWithDeferredConfirmWithContext(pctx, p.exchange, rk, false, false, pub)
}

// Close releases the connection.
func (p *Publisher) Close() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.reset()
}
