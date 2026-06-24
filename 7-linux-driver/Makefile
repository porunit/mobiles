# Iteration 1 — user-space emulator. Linux-only (uses clock_nanosleep / mkfifo).
# On macOS build/run via Docker:  docker compose -f docker/docker-compose.yaml --profile apps build driver-container
CC      ?= cc
CFLAGS  ?= -O2 -Wall -Wextra
BIN     := quote_gen
SRC     := userspace/quote_gen.c

.PHONY: all run clean
all: $(BIN)

$(BIN): $(SRC)
	$(CC) $(CFLAGS) -o $@ $(SRC)

# stream into a local FIFO for manual testing: `cat ./financial_quotes` in another shell
run: $(BIN)
	DEVICE_PATH=./financial_quotes QUOTE_RATE_HZ=5 LOG_LEVEL=debug ./$(BIN)

clean:
	rm -f $(BIN) financial_quotes
