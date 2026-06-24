# syntax=docker/dockerfile:1
FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/receiver .

FROM alpine:3.20
RUN apk add --no-cache wget ca-certificates
COPY --from=build /out/receiver /usr/local/bin/receiver
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/receiver"]
