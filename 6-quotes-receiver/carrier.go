package main

import amqp "github.com/rabbitmq/amqp091-go"

// amqpCarrier adapts an AMQP header table to the OTel TextMapCarrier interface
// so the trace context propagates to downstream consumers (db-service).
type amqpCarrier amqp.Table

func (c amqpCarrier) Get(key string) string {
	if v, ok := c[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

func (c amqpCarrier) Set(key, value string) { c[key] = value }

func (c amqpCarrier) Keys() []string {
	keys := make([]string, 0, len(c))
	for k := range c {
		keys = append(keys, k)
	}
	return keys
}
