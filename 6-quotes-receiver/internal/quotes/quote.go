// Package quotes defines the QuoteTick wire model and NDJSON parsing.
package quotes

import (
	"encoding/json"
	"errors"
)

// QuoteTick is one quote line as emitted by the driver (NDJSON, snake_case).
//
//	{"symbol":"SBER","bid":285.5,"ask":285.62,"spread":0.12,
//	 "timestamp":1750000000000,"volume":200000,"change_percent":0.34}
type QuoteTick struct {
	Symbol        string  `json:"symbol"`
	Bid           float64 `json:"bid"`
	Ask           float64 `json:"ask"`
	Spread        float64 `json:"spread"`
	Timestamp     int64   `json:"timestamp"`
	Volume        uint64  `json:"volume"`
	ChangePercent float64 `json:"change_percent"`
}

// ErrEmpty is returned for blank lines (skipped, not a real parse failure).
var ErrEmpty = errors.New("empty line")

// Parse decodes one NDJSON line into a QuoteTick and validates the symbol.
func Parse(line []byte) (QuoteTick, error) {
	var q QuoteTick
	if len(line) == 0 {
		return q, ErrEmpty
	}
	if err := json.Unmarshal(line, &q); err != nil {
		return q, err
	}
	if q.Symbol == "" {
		return q, errors.New("quote missing symbol")
	}
	return q, nil
}
