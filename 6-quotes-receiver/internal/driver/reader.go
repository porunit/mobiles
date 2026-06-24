// Package driver reads NDJSON quote lines from the driver device (a FIFO in
// iteration 1, a kernel char-device later — same wire contract).
package driver

import (
	"bufio"
	"context"
	"log/slog"
	"os"
	"time"
)

// Reader streams newline-delimited lines from a device path, reopening it when
// the writer disconnects (FIFO EOF), until the context is cancelled.
type Reader struct {
	path string
	log  *slog.Logger
}

func New(path string, log *slog.Logger) *Reader {
	return &Reader{path: path, log: log}
}

// Run opens the device and invokes handle for every non-empty line. Opening a
// FIFO blocks until a writer is present, so this naturally waits for the driver.
func (r *Reader) Run(ctx context.Context, handle func([]byte)) error {
	for {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		f, err := os.Open(r.path) // blocks until the FIFO has a writer
		if err != nil {
			r.log.Warn("open device failed; retrying", "path", r.path, "err", err)
			if !sleepCtx(ctx, time.Second) {
				return ctx.Err()
			}
			continue
		}
		r.log.Info("device opened", "path", r.path)

		sc := bufio.NewScanner(f)
		sc.Buffer(make([]byte, 0, 64*1024), 1024*1024) // tolerate long lines
		for sc.Scan() {
			if ctx.Err() != nil {
				f.Close()
				return ctx.Err()
			}
			line := sc.Bytes()
			if len(line) == 0 {
				continue
			}
			b := make([]byte, len(line)) // copy: scanner reuses its buffer
			copy(b, line)
			handle(b)
		}
		scanErr := sc.Err()
		f.Close()
		if scanErr != nil {
			r.log.Warn("device read error; reopening", "err", scanErr)
		} else {
			r.log.Info("device EOF; writer disconnected, reopening")
		}
		if !sleepCtx(ctx, 500*time.Millisecond) {
			return ctx.Err()
		}
	}
}

func sleepCtx(ctx context.Context, d time.Duration) bool {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-t.C:
		return true
	}
}
