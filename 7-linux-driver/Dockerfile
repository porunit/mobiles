# syntax=docker/dockerfile:1
# ---------------------------------------------------------------------------
# Iteration 1: user-space exchange emulator (NDJSON quotes over a FIFO).
# This is the cross-platform stand-in for the Linux kernel char-device driver;
# the real .ko (step 2) will ship behind a Dockerfile target and reuse the
# same NDJSON wire contract, so 6-quotes-receiver stays unchanged.
# ---------------------------------------------------------------------------
FROM alpine:3.20 AS build
RUN apk add --no-cache build-base
WORKDIR /src
COPY userspace/quote_gen.c .
RUN gcc -O2 -Wall -Wextra -static -o quote_gen quote_gen.c

FROM alpine:3.20
COPY --from=build /src/quote_gen /usr/local/bin/quote_gen
# The FIFO is created on the shared volume mounted at /shared-dev.
ENV DEVICE_PATH=/shared-dev/financial_quotes
ENTRYPOINT ["/usr/local/bin/quote_gen"]
